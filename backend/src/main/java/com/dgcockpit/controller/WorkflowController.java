package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.repository.InstructionRepository;
import com.dgcockpit.service.WorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Expose les transitions du moteur de workflow.
 * Base path : /api/workflow
 * <p>
 * Toutes les opérations requièrent un utilisateur authentifié (attribut {@code currentUser}
 * positionné par {@link com.dgcockpit.filter.AuthFilter}).
 */
@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final InstructionRepository instructionRepo;

    public WorkflowController(WorkflowService workflowService,
                               InstructionRepository instructionRepo) {
        this.workflowService  = workflowService;
        this.instructionRepo  = instructionRepo;
    }

    /** Lance le circuit de validation pour une instruction en BROUILLON. */
    @PostMapping("/{id}/lancer")
    public ResponseEntity<Map<String, Object>> lancer(@PathVariable String id,
                                                       HttpServletRequest request) {
        AppUser user = requireUser(request);
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(workflowService.lancerCircuit(id, user));
    }

    /** Valide l'étape courante pour l'acteur connecté. */
    @PostMapping("/{id}/valider")
    public ResponseEntity<Map<String, Object>> valider(@PathVariable String id,
                                                        HttpServletRequest request) {
        AppUser user = requireUser(request);
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(workflowService.validerEtapeActuelle(id, user));
    }

    /** Rejette l'étape courante et clôture négativement. */
    @PostMapping("/{id}/rejeter")
    public ResponseEntity<Map<String, Object>> rejeter(@PathVariable String id,
                                                        @RequestBody(required = false) Map<String, String> body,
                                                        HttpServletRequest request) {
        AppUser user = requireUser(request);
        if (user == null) return ResponseEntity.status(401).build();
        String motif = body != null ? body.get("motif") : null;
        return ResponseEntity.ok(workflowService.rejeterEtapeActuelle(id, user, motif));
    }

    /** Retourne l'état courant du workflow sans effectuer de transition. */
    @GetMapping("/{id}/etat")
    public ResponseEntity<Map<String, Object>> etat(@PathVariable String id) {
        Instruction instruction = instructionRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Instruction introuvable : " + id));
        return ResponseEntity.ok(workflowService.toEtatDto(instruction));
    }

    private AppUser requireUser(HttpServletRequest request) {
        return (AppUser) request.getAttribute("currentUser");
    }
}
