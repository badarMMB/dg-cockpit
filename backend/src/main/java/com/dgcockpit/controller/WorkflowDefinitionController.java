package com.dgcockpit.controller;

import com.dgcockpit.dto.workflow.WorkflowDefinitionDTO;
import com.dgcockpit.dto.workflow.WorkflowParticipantDTO;
import com.dgcockpit.dto.workflow.WorkflowStepDTO;
import com.dgcockpit.entity.*;
import com.dgcockpit.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * API CRUD pour la configuration des WorkflowDefinition, leurs étapes et participants.
 * Sécurité : CAN_MANAGE_TYPES sur toutes les opérations d'écriture.
 */
@RestController
@RequestMapping("/api/workflows")
@CrossOrigin(origins = "*")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowStepRepository stepRepo;
    private final WorkflowParticipantRepository participantRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final PosteRepository posteRepo;

    public WorkflowDefinitionController(WorkflowDefinitionRepository definitionRepo,
                                         WorkflowStepRepository stepRepo,
                                         WorkflowParticipantRepository participantRepo,
                                         WorkflowInstanceRepository instanceRepo,
                                         PosteRepository posteRepo) {
        this.definitionRepo = definitionRepo;
        this.stepRepo = stepRepo;
        this.participantRepo = participantRepo;
        this.instanceRepo = instanceRepo;
        this.posteRepo = posteRepo;
    }

    // ── WorkflowDefinition CRUD ───────────────────────────────────────────────

    @GetMapping
    public List<WorkflowDefinitionDTO> list() {
        return definitionRepo.findAll().stream()
                .map(this::toDefinitionDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public WorkflowDefinitionDTO create(@RequestBody Map<String, Object> body) {
        WorkflowDefinition def = new WorkflowDefinition();
        applyDefinitionFields(def, body);
        def.setVersion(1);
        return toDefinitionDto(definitionRepo.save(def));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<WorkflowDefinitionDTO> update(@PathVariable String id,
                                                         @RequestBody Map<String, Object> body) {
        WorkflowDefinition def = definitionRepo.findById(id).orElse(null);
        if (def == null) return ResponseEntity.notFound().build();
        applyDefinitionFields(def, body);
        def.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(toDefinitionDto(definitionRepo.save(def)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!instanceRepo.findByWorkflowDefinitionId(id).isEmpty()) {
            return ResponseEntity.status(409).build(); // instances actives → refus
        }
        // Supprimer participants → étapes → définition
        stepRepo.findByWorkflowIdOrderByOrdre(id).forEach(s -> {
            participantRepo.deleteByWorkflowStepId(s.getId());
            stepRepo.delete(s);
        });
        definitionRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<WorkflowDefinitionDTO> toggle(@PathVariable String id) {
        WorkflowDefinition def = definitionRepo.findById(id).orElse(null);
        if (def == null) return ResponseEntity.notFound().build();
        def.setActif(!def.isActif());
        def.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(toDefinitionDto(definitionRepo.save(def)));
    }

    // ── WorkflowStep CRUD ─────────────────────────────────────────────────────

    @GetMapping("/{id}/steps")
    public List<WorkflowStepDTO> listSteps(@PathVariable String id) {
        return stepRepo.findByWorkflowIdOrderByOrdre(id).stream()
                .map(this::toStepDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/steps")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<WorkflowStepDTO> createStep(@PathVariable String id,
                                                       @RequestBody Map<String, Object> body) {
        if (!definitionRepo.existsById(id)) return ResponseEntity.notFound().build();
        WorkflowStep step = new WorkflowStep();
        step.setWorkflowId(id);
        applyStepFields(step, body);
        return ResponseEntity.ok(toStepDto(stepRepo.save(step)));
    }

    @PutMapping("/{id}/steps/{stepId}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<WorkflowStepDTO> updateStep(@PathVariable String id,
                                                       @PathVariable String stepId,
                                                       @RequestBody Map<String, Object> body) {
        WorkflowStep step = stepRepo.findById(stepId).orElse(null);
        if (step == null || !step.getWorkflowId().equals(id)) return ResponseEntity.notFound().build();
        applyStepFields(step, body);
        step.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(toStepDto(stepRepo.save(step)));
    }

    @DeleteMapping("/{id}/steps/{stepId}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    @Transactional
    public ResponseEntity<Void> deleteStep(@PathVariable String id,
                                            @PathVariable String stepId) {
        WorkflowStep step = stepRepo.findById(stepId).orElse(null);
        if (step == null || !step.getWorkflowId().equals(id)) return ResponseEntity.notFound().build();
        participantRepo.deleteByWorkflowStepId(stepId);
        stepRepo.delete(step);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/steps/reorder")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<List<WorkflowStepDTO>> reorderSteps(@PathVariable String id,
                                                               @RequestBody List<String> orderedStepIds) {
        AtomicInteger ordre = new AtomicInteger(0);
        orderedStepIds.forEach(stepId -> stepRepo.findById(stepId).ifPresent(s -> {
            if (s.getWorkflowId().equals(id)) {
                s.setOrdre(ordre.getAndIncrement());
                s.setUpdatedAt(LocalDateTime.now());
                stepRepo.save(s);
            }
        }));
        return ResponseEntity.ok(listSteps(id));
    }

    // ── WorkflowParticipant ───────────────────────────────────────────────────

    @GetMapping("/{id}/steps/{stepId}/participants")
    public List<WorkflowParticipantDTO> listParticipants(@PathVariable String id,
                                                          @PathVariable String stepId) {
        return participantRepo.findByWorkflowStepId(stepId).stream()
                .map(this::toParticipantDto)
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}/steps/{stepId}/participants")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    @Transactional
    public List<WorkflowParticipantDTO> saveParticipants(@PathVariable String id,
                                                          @PathVariable String stepId,
                                                          @RequestBody List<Map<String, Object>> body) {
        participantRepo.deleteByWorkflowStepId(stepId);
        AtomicInteger ordre = new AtomicInteger(0);
        body.forEach(p -> {
            String posteId = (String) p.get("posteId");
            String roleStr = (String) p.get("roleParticipant");
            if (posteId == null || posteId.isBlank() || roleStr == null) return;
            WorkflowParticipant wp = new WorkflowParticipant();
            wp.setWorkflowStepId(stepId);
            wp.setPosteId(posteId);
            wp.setRoleParticipant(RoleParticipant.valueOf(roleStr));
            wp.setObligatoire(Boolean.TRUE.equals(p.get("obligatoire")));
            wp.setOrdre(ordre.getAndIncrement());
            participantRepo.save(wp);
        });
        return listParticipants(id, stepId);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public Map<String, Object> validate(@PathVariable String id) {
        List<String> errors = new ArrayList<>();
        List<WorkflowStep> steps = stepRepo.findByWorkflowIdOrderByOrdre(id);

        if (steps.isEmpty()) {
            errors.add("Le workflow doit avoir au moins une étape.");
        } else {
            // Vérifier qu'il y a au moins 1 étape END
            boolean hasEnd = steps.stream().anyMatch(s -> s.getStepType() == StepType.END);
            if (!hasEnd) errors.add("Le workflow doit avoir au moins une étape de type END.");

            // Vérifier l'unicité des ordres
            long distinctOrders = steps.stream().map(WorkflowStep::getOrdre).distinct().count();
            if (distinctOrders < steps.size()) errors.add("Les ordres des étapes doivent être uniques.");

            // Vérifier que les nextStepId référencent des étapes existantes dans ce workflow
            Set<String> stepIds = steps.stream().map(WorkflowStep::getId).collect(Collectors.toSet());
            steps.stream()
                 .filter(s -> s.getNextStepId() != null && !s.getNextStepId().isBlank())
                 .filter(s -> !stepIds.contains(s.getNextStepId()))
                 .forEach(s -> errors.add("L'étape \"" + s.getLibelle() + "\" référence un nextStepId inexistant."));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        return result;
    }

    // ── Helpers de mapping ────────────────────────────────────────────────────

    private void applyDefinitionFields(WorkflowDefinition def, Map<String, Object> body) {
        if (body.containsKey("code"))        def.setCode((String) body.get("code"));
        if (body.containsKey("libelle"))     def.setLibelle((String) body.get("libelle"));
        if (body.containsKey("description")) def.setDescription((String) body.get("description"));
        if (body.containsKey("actif"))       def.setActif(Boolean.TRUE.equals(body.get("actif")));
    }

    private void applyStepFields(WorkflowStep step, Map<String, Object> body) {
        if (body.containsKey("code"))           step.setCode((String) body.get("code"));
        if (body.containsKey("libelle"))        step.setLibelle((String) body.get("libelle"));
        if (body.containsKey("description"))    step.setDescription((String) body.get("description"));
        if (body.containsKey("ordre"))          step.setOrdre(((Number) body.get("ordre")).intValue());
        if (body.containsKey("autoTransition")) step.setAutoTransition(Boolean.TRUE.equals(body.get("autoTransition")));
        if (body.containsKey("nextStepId"))     step.setNextStepId((String) body.get("nextStepId"));
        if (body.containsKey("configJson"))     step.setConfigJson((String) body.get("configJson"));
        if (body.containsKey("stepType")) {
            try { step.setStepType(StepType.valueOf((String) body.get("stepType"))); }
            catch (Exception ignored) {}
        }
    }

    private WorkflowDefinitionDTO toDefinitionDto(WorkflowDefinition def) {
        WorkflowDefinitionDTO dto = new WorkflowDefinitionDTO();
        dto.setId(def.getId());
        dto.setCode(def.getCode());
        dto.setLibelle(def.getLibelle());
        dto.setDescription(def.getDescription());
        dto.setActif(def.isActif());
        dto.setVersion(def.getVersion());
        dto.setCreatedAt(def.getCreatedAt());
        dto.setUpdatedAt(def.getUpdatedAt());
        return dto;
    }

    private WorkflowStepDTO toStepDto(WorkflowStep s) {
        WorkflowStepDTO dto = new WorkflowStepDTO();
        dto.setId(s.getId());
        dto.setWorkflowId(s.getWorkflowId());
        dto.setOrdre(s.getOrdre());
        dto.setCode(s.getCode());
        dto.setLibelle(s.getLibelle());
        dto.setDescription(s.getDescription());
        dto.setStepType(s.getStepType());
        dto.setAutoTransition(s.isAutoTransition());
        dto.setNextStepId(s.getNextStepId());
        dto.setConfigJson(s.getConfigJson());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }

    private WorkflowParticipantDTO toParticipantDto(WorkflowParticipant wp) {
        WorkflowParticipantDTO dto = new WorkflowParticipantDTO();
        dto.setId(wp.getId());
        dto.setWorkflowStepId(wp.getWorkflowStepId());
        dto.setPosteId(wp.getPosteId());
        dto.setRoleParticipant(wp.getRoleParticipant());
        dto.setObligatoire(wp.isObligatoire());
        dto.setOrdre(wp.getOrdre());
        posteRepo.findById(wp.getPosteId())
                 .ifPresent(p -> dto.setPosteLibelle(p.getLibelle()));
        return dto;
    }
}
