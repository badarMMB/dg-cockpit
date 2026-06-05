package com.dgcockpit.controller;

import com.dgcockpit.dto.workflow.WorkflowActionDTO;
import com.dgcockpit.dto.workflow.WorkflowInstanceDTO;
import com.dgcockpit.entity.*;
import com.dgcockpit.repository.*;
import com.dgcockpit.service.AuthorizationService;
import com.dgcockpit.service.WorkflowEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workflow-instances")
@CrossOrigin(origins = "*")
public class WorkflowInstanceController {

    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowActionRepository actionRepo;
    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowStepRepository stepRepo;
    private final AppUserRepository userRepo;
    private final AuthorizationService authorizationService;
    private final WorkflowEngineService workflowEngine;

    public WorkflowInstanceController(WorkflowInstanceRepository instanceRepo,
                                       WorkflowActionRepository actionRepo,
                                       WorkflowDefinitionRepository definitionRepo,
                                       WorkflowStepRepository stepRepo,
                                       AppUserRepository userRepo,
                                       AuthorizationService authorizationService,
                                       WorkflowEngineService workflowEngine) {
        this.instanceRepo         = instanceRepo;
        this.actionRepo           = actionRepo;
        this.definitionRepo       = definitionRepo;
        this.stepRepo             = stepRepo;
        this.userRepo             = userRepo;
        this.authorizationService = authorizationService;
        this.workflowEngine       = workflowEngine;
    }

    /** Instances liées à l'utilisateur courant (créées par lui). */
    @GetMapping("/my")
    public List<WorkflowInstanceDTO> myInstances() {
        AppUser current = authorizationService.currentUser();
        return instanceRepo.findByCreatedById(current.getId()).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /** Détail d'une instance. */
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowInstanceDTO> getById(@PathVariable String id) {
        return instanceRepo.findById(id)
            .map(i -> ResponseEntity.ok(toDto(i)))
            .orElse(ResponseEntity.notFound().build());
    }

    /** Historique des actions d'une instance. */
    @GetMapping("/{id}/actions")
    public ResponseEntity<List<WorkflowActionDTO>> getActions(@PathVariable String id) {
        if (!instanceRepo.existsById(id)) return ResponseEntity.notFound().build();
        List<WorkflowActionDTO> actions = actionRepo
            .findByWorkflowInstanceIdOrderByCreatedAtDesc(id).stream()
            .map(this::toActionDto)
            .collect(Collectors.toList());
        return ResponseEntity.ok(actions);
    }

    /** Avancer manuellement vers l'étape suivante. */
    @PostMapping("/{id}/next-step")
    public ResponseEntity<WorkflowInstanceDTO> nextStep(@PathVariable String id) {
        AppUser current = authorizationService.currentUser();
        try {
            workflowEngine.moveToNextStep(id, current.getId());
            return instanceRepo.findById(id)
                .map(i -> ResponseEntity.ok(toDto(i)))
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** Annuler une instance en cours. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String id,
                                        @RequestBody(required = false) Map<String, String> body) {
        AppUser current = authorizationService.currentUser();
        String motif = body != null ? body.get("motif") : null;
        try {
            workflowEngine.cancelWorkflow(id, current.getId(), motif);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private WorkflowInstanceDTO toDto(WorkflowInstance i) {
        WorkflowInstanceDTO dto = new WorkflowInstanceDTO();
        dto.setId(i.getId());
        dto.setWorkflowDefinitionId(i.getWorkflowDefinitionId());
        dto.setStatus(i.getStatus());
        dto.setStartedAt(i.getStartedAt());
        dto.setCompletedAt(i.getCompletedAt());
        dto.setSourceDocumentId(i.getSourceDocumentId());
        dto.setSourceInstructionId(i.getSourceInstructionId());
        dto.setCreatedById(i.getCreatedById());

        definitionRepo.findById(i.getWorkflowDefinitionId())
            .ifPresent(d -> dto.setWorkflowLibelle(d.getLibelle()));

        if (i.getCurrentStepId() != null) {
            dto.setCurrentStepId(i.getCurrentStepId());
            stepRepo.findById(i.getCurrentStepId())
                .ifPresent(s -> dto.setCurrentStepLibelle(s.getLibelle()));
        }
        if (i.getCreatedById() != null) {
            userRepo.findById(i.getCreatedById())
                .ifPresent(u -> dto.setCreatedByNom(u.getNomComplet()));
        }
        return dto;
    }

    private WorkflowActionDTO toActionDto(WorkflowAction a) {
        WorkflowActionDTO dto = new WorkflowActionDTO();
        dto.setId(a.getId());
        dto.setWorkflowInstanceId(a.getWorkflowInstanceId());
        dto.setStepId(a.getStepId());
        dto.setAction(a.getAction());
        dto.setCommentaire(a.getCommentaire());
        dto.setCreatedAt(a.getCreatedAt());
        dto.setUserId(a.getUserId());

        if (a.getStepId() != null) {
            stepRepo.findById(a.getStepId()).ifPresent(s -> dto.setStepLibelle(s.getLibelle()));
        }
        if (a.getUserId() != null) {
            userRepo.findById(a.getUserId()).ifPresent(u -> dto.setUserNom(u.getNomComplet()));
        }
        return dto;
    }
}
