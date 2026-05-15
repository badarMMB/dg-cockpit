package com.dgcockpit.controller;

import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.ValidationStep;
import com.dgcockpit.repository.InstructionRepository;
import com.dgcockpit.repository.ValidationStepRepository;
import com.dgcockpit.service.AuditService;
import com.dgcockpit.sse.SseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/instructions")
public class WorkflowController {

    private final InstructionRepository instructionRepo;
    private final ValidationStepRepository stepRepo;
    private final SseService sseService;
    private final AuditService auditService;

    public WorkflowController(InstructionRepository instructionRepo,
                               ValidationStepRepository stepRepo,
                               SseService sseService,
                               AuditService auditService) {
        this.instructionRepo = instructionRepo;
        this.stepRepo = stepRepo;
        this.sseService = sseService;
        this.auditService = auditService;
    }

    @PostMapping("/{id}/soumettre")
    public ResponseEntity<Map<String, Object>> soumettre(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        return instructionRepo.findById(id).map(instruction -> {
            instruction.setStatut(Instruction.StatutInstruction.SOUMIS_VALIDATION);
            instructionRepo.save(instruction);

            ValidationStep step = new ValidationStep();
            step.setInstruction(instruction);
            step.setStatut(ValidationStep.Statut.SOUMIS);
            step.setValidateur(body.getOrDefault("validateur", "DG"));
            step.setCommentaire(body.getOrDefault("commentaire", ""));
            stepRepo.save(step);

            sseService.broadcast("STATUT_CHANGE", Map.of(
                "instructionId", id,
                "statut", "SOUMIS_VALIDATION"
            ));
            auditService.log("SOUMETTRE", "instruction", id,
                "Soumis par " + body.getOrDefault("validateur", "DG"));

            return ResponseEntity.ok(toDto(step));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/valider")
    public ResponseEntity<Map<String, Object>> valider(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        return instructionRepo.findById(id).map(instruction -> {
            instruction.setStatut(Instruction.StatutInstruction.CLOTURE);
            instructionRepo.save(instruction);

            ValidationStep step = new ValidationStep();
            step.setInstruction(instruction);
            step.setStatut(ValidationStep.Statut.VALIDE);
            step.setValidateur(body.getOrDefault("validateur", "DG"));
            step.setCommentaire(body.getOrDefault("commentaire", ""));
            stepRepo.save(step);

            sseService.broadcast("STATUT_CHANGE", Map.of(
                "instructionId", id,
                "statut", "CLOTURE"
            ));
            auditService.log("VALIDER", "instruction", id,
                "Validé par " + body.getOrDefault("validateur", "DG")
                + (body.containsKey("commentaire") ? " — " + body.get("commentaire") : ""));

            return ResponseEntity.ok(toDto(step));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/rejeter")
    public ResponseEntity<Map<String, Object>> rejeter(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        return instructionRepo.findById(id).map(instruction -> {
            instruction.setStatut(Instruction.StatutInstruction.REFUSE);
            instructionRepo.save(instruction);

            ValidationStep step = new ValidationStep();
            step.setInstruction(instruction);
            step.setStatut(ValidationStep.Statut.REJETE);
            step.setValidateur(body.getOrDefault("validateur", "DG"));
            step.setCommentaire(body.getOrDefault("commentaire", ""));
            stepRepo.save(step);

            sseService.broadcast("STATUT_CHANGE", Map.of(
                "instructionId", id,
                "statut", "REFUSE"
            ));
            auditService.log("REJETER", "instruction", id,
                "Rejeté par " + body.getOrDefault("validateur", "DG")
                + (body.containsKey("commentaire") ? " — " + body.get("commentaire") : ""));

            return ResponseEntity.ok(toDto(step));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/workflow")
    public List<Map<String, Object>> getWorkflow(@PathVariable String id) {
        return stepRepo.findByInstructionIdOrderByDateAsc(id)
                .stream().map(this::toDto).toList();
    }

    private Map<String, Object> toDto(ValidationStep s) {
        return Map.<String, Object>of(
            "id", s.getId(),
            "statut", s.getStatut().name(),
            "validateur", s.getValidateur(),
            "commentaire", s.getCommentaire() != null ? s.getCommentaire() : "",
            "date", s.getDate().toString()
        );
    }
}
