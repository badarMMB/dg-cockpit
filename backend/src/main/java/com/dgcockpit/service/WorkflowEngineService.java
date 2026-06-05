package com.dgcockpit.service;

import com.dgcockpit.entity.*;
import com.dgcockpit.repository.*;
import com.dgcockpit.sse.SseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Moteur d'exécution des WorkflowInstance.
 *
 * Principes de coexistence :
 *  - Si workflowDefinitionId == null sur le TypeDocument → flux legacy inchangé.
 *  - Ce service n'est appelé que lorsqu'un workflow est explicitement associé.
 */
@Service
public class WorkflowEngineService {

    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowStepRepository stepRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowActionRepository actionRepo;
    private final WorkflowParticipantResolverService participantResolver;
    private final InstructionRepository instructionRepo;
    private final AssigneeRepository assigneeRepo;
    private final CircuitSignatureRepository circuitRepo;
    private final PdfDocumentRepository pdfRepo;
    private final BureauDocumentRepository bureauRepo;
    private final PageAnnotationRepository annotRepo;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    public WorkflowEngineService(WorkflowDefinitionRepository definitionRepo,
                                  WorkflowStepRepository stepRepo,
                                  WorkflowInstanceRepository instanceRepo,
                                  WorkflowActionRepository actionRepo,
                                  WorkflowParticipantResolverService participantResolver,
                                  InstructionRepository instructionRepo,
                                  AssigneeRepository assigneeRepo,
                                  CircuitSignatureRepository circuitRepo,
                                  PdfDocumentRepository pdfRepo,
                                  BureauDocumentRepository bureauRepo,
                                  PageAnnotationRepository annotRepo,
                                  SseService sseService,
                                  ObjectMapper objectMapper) {
        this.definitionRepo     = definitionRepo;
        this.stepRepo           = stepRepo;
        this.instanceRepo       = instanceRepo;
        this.actionRepo         = actionRepo;
        this.participantResolver = participantResolver;
        this.instructionRepo    = instructionRepo;
        this.assigneeRepo       = assigneeRepo;
        this.circuitRepo        = circuitRepo;
        this.pdfRepo            = pdfRepo;
        this.bureauRepo         = bureauRepo;
        this.annotRepo          = annotRepo;
        this.sseService         = sseService;
        this.objectMapper       = objectMapper;
    }

    // ── Démarrage ─────────────────────────────────────────────────────────────

    @Transactional
    public WorkflowInstance startWorkflow(String definitionId,
                                           String sourceDocumentId,
                                           String sourceInstructionId,
                                           String userId) {
        WorkflowDefinition def = definitionRepo.findById(definitionId)
            .orElseThrow(() -> new IllegalArgumentException("WorkflowDefinition introuvable: " + definitionId));

        List<WorkflowStep> steps = stepRepo.findByWorkflowIdOrderByOrdre(definitionId);
        if (steps.isEmpty())
            throw new IllegalStateException("Le workflow \"" + def.getLibelle() + "\" n'a aucune étape.");

        WorkflowStep firstStep = steps.get(0);

        WorkflowInstance instance = new WorkflowInstance();
        instance.setWorkflowDefinitionId(definitionId);
        instance.setCurrentStepId(firstStep.getId());
        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setStartedAt(LocalDateTime.now());
        instance.setSourceDocumentId(sourceDocumentId);
        instance.setSourceInstructionId(sourceInstructionId);
        instance.setCreatedById(userId);
        instanceRepo.save(instance);

        logAction(instance.getId(), firstStep.getId(), userId, WorkflowActionType.ENTER_STEP, null);
        executeStep(instance, firstStep, userId);
        return instance;
    }

    // ── Avancement ────────────────────────────────────────────────────────────

    @Transactional
    public void moveToNextStep(String instanceId, String userId) {
        WorkflowInstance instance = requireRunning(instanceId);
        WorkflowStep currentStep  = stepRepo.findById(instance.getCurrentStepId())
            .orElseThrow(() -> new IllegalStateException("Étape courante introuvable"));

        logAction(instanceId, currentStep.getId(), userId, WorkflowActionType.CLOTURER, null);

        if (currentStep.getStepType() == StepType.END) {
            completeWorkflow(instanceId, userId);
            return;
        }

        if (currentStep.getNextStepId() == null || currentStep.getNextStepId().isBlank()) {
            completeWorkflow(instanceId, userId);
            return;
        }

        WorkflowStep nextStep = stepRepo.findById(currentStep.getNextStepId())
            .orElseThrow(() -> new IllegalStateException("Étape suivante introuvable: " + currentStep.getNextStepId()));

        instance.setCurrentStepId(nextStep.getId());
        instanceRepo.save(instance);

        logAction(instanceId, nextStep.getId(), userId, WorkflowActionType.ENTER_STEP, null);
        executeStep(instance, nextStep, userId);
    }

    @Transactional
    public void completeWorkflow(String instanceId, String userId) {
        WorkflowInstance instance = instanceRepo.findById(instanceId)
            .orElseThrow(() -> new IllegalArgumentException("WorkflowInstance introuvable: " + instanceId));
        if (instance.getStatus() == WorkflowStatus.COMPLETED) return;

        instance.setStatus(WorkflowStatus.COMPLETED);
        instance.setCompletedAt(LocalDateTime.now());
        instanceRepo.save(instance);

        logAction(instanceId, instance.getCurrentStepId(), userId, WorkflowActionType.COMPLETE, null);
        sseService.broadcast("WORKFLOW_COMPLETED", Map.of("instanceId", instanceId));
    }

    @Transactional
    public void cancelWorkflow(String instanceId, String userId, String motif) {
        WorkflowInstance instance = requireRunning(instanceId);
        instance.setStatus(WorkflowStatus.CANCELLED);
        instance.setCompletedAt(LocalDateTime.now());
        instanceRepo.save(instance);

        logAction(instanceId, instance.getCurrentStepId(), userId, WorkflowActionType.CANCEL, motif);
        sseService.broadcast("WORKFLOW_CANCELLED", Map.of("instanceId", instanceId));
    }

    // ── Exécution d'une étape ─────────────────────────────────────────────────

    private void executeStep(WorkflowInstance instance, WorkflowStep step, String userId) {
        switch (step.getStepType()) {
            case INSTRUCTION       -> handleInstruction(instance, step, userId);
            case SIGNATURE         -> handleSignature(instance, step, userId);
            case TASK, NOTIFICATION-> handleTask(instance, step);
            case END               -> completeWorkflow(instance.getId(), userId);
            // DOCUMENT_CREATION, VALIDATION, DECISION → manuel, rien à auto-exécuter
            default                -> { /* attente action manuelle */ }
        }

        // Auto-transition : avancer immédiatement si configuré (sauf END déjà traité)
        if (step.isAutoTransition() && step.getStepType() != StepType.END) {
            moveToNextStep(instance.getId(), userId);
        }
    }

    // ── Handlers par type ─────────────────────────────────────────────────────

    /**
     * Crée une Instruction et ses Assignees à partir des WorkflowParticipant de l'étape.
     */
    private void handleInstruction(WorkflowInstance instance, WorkflowStep step, String userId) {
        Instruction instr = new Instruction();
        instr.setTitle(step.getLibelle());
        instr.setType(step.getCode());
        instr.setStatut(Instruction.StatutInstruction.EN_COURS);
        instr.setCreatedById(userId);
        instr.setUrgence("NORMAL");
        instr.setWorkflowInstanceId(instance.getId());
        instr.setWorkflowStepId(step.getId());
        Instruction saved = instructionRepo.save(instr);

        participantResolver.resolveParticipants(step.getId()).forEach(user -> {
            Assignee a = new Assignee();
            a.setInstruction(saved);
            a.setUserId(user.getId());
            a.setAgent(user.getNomComplet());
            assigneeRepo.save(a);
        });

        sseService.broadcast("INSTRUCTION_CREATED", Map.of(
            "instructionId", saved.getId(), "instanceId", instance.getId()));
    }

    /**
     * Crée le circuit de signature pour le PdfDocument source.
     *
     * Résolution des signataires (par ordre de priorité) :
     *  1. circuitJson explicite dans step.configJson
     *  2. Participants SIGNATAIRE définis sur l'étape WorkflowParticipant
     *
     * Zones nominatives : chaque zone dans BureauDocument.signatureZonesJson peut contenir
     * un champ "userId". Pour chaque étape du circuit, seules les zones dont "userId" correspond
     * au signataire sont copiées dans CircuitSignature.signatureZonesJson.
     * Si aucune zone n'est taguée (flux legacy), toutes les zones sont copiées pour chaque étape.
     *
     * Pour l'étape 0 (premier signataire), des PageAnnotations SIGNATURE_ZONE sont créées
     * depuis ses zones filtrées, afin que le parapheur puisse les afficher immédiatement.
     */
    private void handleSignature(WorkflowInstance instance, WorkflowStep step, String userId) {
        if (instance.getSourceDocumentId() == null) return;

        PdfDocument pdf = pdfRepo.findById(instance.getSourceDocumentId()).orElse(null);
        if (pdf == null) return;

        // Résoudre la liste des signataires
        List<Map<String, Object>> circuitSteps = parseCircuitJson(step.getConfigJson());

        if (circuitSteps.isEmpty()) {
            circuitSteps = participantResolver
                .resolveByRole(step.getId(), RoleParticipant.SIGNATAIRE)
                .stream()
                .map(u -> (Map<String, Object>) Map.<String, Object>of(
                    "userId", u.getId(), "nom", u.getNomComplet()))
                .toList();
        }

        if (circuitSteps.isEmpty()) return;

        // Récupérer toutes les zones nominatives depuis le BureauDocument source
        List<Map<String, Object>> allZones;
        java.util.Optional<com.dgcockpit.entity.BureauDocument> bdOpt = bureauRepo.findByPdfDocumentId(pdf.getId());
        if (bdOpt.isPresent()) {
            allZones = parseZonesList(bdOpt.get().getSignatureZonesJson());
        } else {
            allZones = List.of();
        }

        boolean hasNominativeZones = allZones.stream().anyMatch(z -> z.get("userId") != null);

        // Supprimer l'ancien circuit s'il existe (re-entrée)
        circuitRepo.findByPdfDocumentIdOrderByStepOrderAsc(pdf.getId()).forEach(circuitRepo::delete);
        // Supprimer les anciennes annotations de l'étape 0
        annotRepo.findAll().stream()
            .filter(a -> a.getPageId().startsWith(pdf.getId() + "::") && "SIGNATURE_ZONE".equals(a.getAnnotationType()))
            .forEach(annotRepo::delete);

        for (int i = 0; i < circuitSteps.size(); i++) {
            Map<String, Object> s = circuitSteps.get(i);
            String signerUserId = (String) s.get("userId");

            List<Map<String, Object>> zonesForSigner = hasNominativeZones
                ? allZones.stream().filter(z -> signerUserId.equals(z.get("userId"))).toList()
                : allZones; // legacy : toutes les zones pour tous

            CircuitSignature etape = new CircuitSignature();
            etape.setPdfDocumentId(pdf.getId());
            etape.setStepOrder(i);
            etape.setSignaireUserId(signerUserId);
            etape.setSignaireNom((String) s.get("nom"));
            // Étape 0 : zones dans PageAnnotations (créées ci-dessous), pas dans le JSON
            // Étapes suivantes : zones stockées dans CircuitSignature pour le bureau de transit
            if (i > 0 && !zonesForSigner.isEmpty()) {
                try { etape.setSignatureZonesJson(objectMapper.writeValueAsString(zonesForSigner)); }
                catch (Exception ignored) {}
            }
            circuitRepo.save(etape);

            // Créer les PageAnnotations pour le premier signataire
            if (i == 0) {
                for (Map<String, Object> z : zonesForSigner) {
                    PageAnnotation a = new PageAnnotation();
                    a.setPageId(pdf.getId() + "::" + ((Number) z.get("page")).intValue());
                    a.setAnnotationType("SIGNATURE_ZONE");
                    a.setXPercent(((Number) z.get("x")).doubleValue());
                    a.setYPercent(((Number) z.get("y")).doubleValue());
                    a.setWidthPercent(((Number) z.get("w")).doubleValue());
                    a.setHeightPercent(((Number) z.get("h")).doubleValue());
                    a.setCreatedBy(userId);
                    annotRepo.save(a);
                }
            }
        }

        pdf.setCurrentSignataireUserId((String) circuitSteps.get(0).get("userId"));
        pdf.setCurrentCircuitStep(0);
        pdf.setParapheurStatut(PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE);
        pdf.setWorkflowInstanceId(instance.getId());
        pdf.setWorkflowStepId(step.getId());
        pdfRepo.save(pdf);

        sseService.broadcast("PARAPHEUR_UPDATED", Map.of("action", "WORKFLOW_SIGNATURE", "id", pdf.getId()));
    }

    /**
     * Notifie via SSE — pas de document créé, juste un événement temps-réel.
     */
    private void handleTask(WorkflowInstance instance, WorkflowStep step) {
        sseService.broadcast("WORKFLOW_TASK", Map.of(
            "instanceId", instance.getId(),
            "stepId",     step.getId(),
            "stepCode",   step.getCode(),
            "stepLibelle", step.getLibelle()
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkflowInstance requireRunning(String instanceId) {
        WorkflowInstance instance = instanceRepo.findById(instanceId)
            .orElseThrow(() -> new IllegalArgumentException("WorkflowInstance introuvable: " + instanceId));
        if (instance.getStatus() != WorkflowStatus.RUNNING)
            throw new IllegalStateException("WorkflowInstance n'est pas en cours (statut: " + instance.getStatus() + ")");
        return instance;
    }

    private void logAction(String instanceId, String stepId, String userId,
                            WorkflowActionType type, String commentaire) {
        WorkflowAction action = new WorkflowAction();
        action.setWorkflowInstanceId(instanceId);
        action.setStepId(stepId);
        action.setUserId(userId);
        action.setAction(type);
        action.setCommentaire(commentaire);
        actionRepo.save(action);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseZonesList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseCircuitJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            Map<String, Object> config = objectMapper.readValue(json,
                new TypeReference<Map<String, Object>>() {});
            Object circuit = config.get("circuitJson");
            if (circuit instanceof String s && !s.isBlank()) {
                return objectMapper.readValue(s, new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception ignored) {}
        return List.of();
    }
}
