package com.dgcockpit.controller;

import com.dgcockpit.entity.*;
import com.dgcockpit.service.ParametresService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parametres")
public class ParametresController {

    private final ParametresService service;
    private final ObjectMapper objectMapper;

    public ParametresController(ParametresService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    // ── InstructionType ───────────────────────────────────────────────────────

    @GetMapping("/instruction-types")
    public List<Map<String, Object>> getInstructionTypes(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        var list = activeOnly ? service.getActiveInstructionTypes() : service.getAllInstructionTypes();
        return list.stream().map(this::toInstructionTypeDto).toList();
    }

    @PostMapping("/instruction-types")
    public Map<String, Object> createInstructionType(@RequestBody Map<String, Object> body) {
        return toInstructionTypeDto(service.createInstructionType(body));
    }

    @PutMapping("/instruction-types/{id}")
    public Map<String, Object> updateInstructionType(@PathVariable String id,
                                                      @RequestBody Map<String, Object> body) {
        return toInstructionTypeDto(service.updateInstructionType(id, body));
    }

    @PatchMapping("/instruction-types/{id}/toggle")
    public ResponseEntity<Void> toggleInstructionType(@PathVariable String id) {
        service.toggleInstructionType(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/instruction-types/{id}")
    public ResponseEntity<Void> deleteInstructionType(@PathVariable String id) {
        service.deleteInstructionType(id);
        return ResponseEntity.noContent().build();
    }

    // ── WorkflowStep ──────────────────────────────────────────────────────────

    @GetMapping("/instruction-types/{typeId}/steps")
    public List<Map<String, Object>> getSteps(@PathVariable String typeId) {
        return service.getStepsForType(typeId).stream().map(this::toStepDto).toList();
    }

    @PostMapping("/instruction-types/{typeId}/steps")
    public Map<String, Object> createStep(@PathVariable String typeId,
                                           @RequestBody Map<String, Object> body) {
        return toStepDto(service.createStep(typeId, body));
    }

    @PutMapping("/instruction-types/{typeId}/steps/{stepId}")
    public Map<String, Object> updateStep(@PathVariable String typeId,
                                           @PathVariable String stepId,
                                           @RequestBody Map<String, Object> body) {
        return toStepDto(service.updateStep(stepId, body));
    }

    @DeleteMapping("/instruction-types/{typeId}/steps/{stepId}")
    public ResponseEntity<Void> deleteStep(@PathVariable String typeId,
                                            @PathVariable String stepId) {
        service.deleteStep(stepId);
        return ResponseEntity.noContent().build();
    }

    // ── Poste ─────────────────────────────────────────────────────────────────

    @GetMapping("/postes")
    public List<Map<String, Object>> getPostes(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        var list = activeOnly ? service.getActivePostes() : service.getAllPostes();
        return list.stream().map(this::toPosteDto).toList();
    }

    @PostMapping("/postes")
    public Map<String, Object> createPoste(@RequestBody Map<String, Object> body) {
        return toPosteDto(service.createPoste(body));
    }

    @PutMapping("/postes/{id}")
    public Map<String, Object> updatePoste(@PathVariable String id,
                                            @RequestBody Map<String, Object> body) {
        return toPosteDto(service.updatePoste(id, body));
    }

    @PatchMapping("/postes/{id}/toggle")
    public ResponseEntity<Void> togglePoste(@PathVariable String id) {
        service.togglePoste(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/postes/{id}")
    public ResponseEntity<Void> deletePoste(@PathVariable String id) {
        service.deletePoste(id);
        return ResponseEntity.noContent().build();
    }

    // ── ProofType ─────────────────────────────────────────────────────────────

    @GetMapping("/proof-types")
    public List<Map<String, Object>> getProofTypes(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        var list = activeOnly ? service.getActiveProofTypes() : service.getAllProofTypes();
        return list.stream().map(this::toProofTypeDto).toList();
    }

    @PostMapping("/proof-types")
    public Map<String, Object> createProofType(@RequestBody Map<String, Object> body) {
        return toProofTypeDto(service.createProofType(body));
    }

    @PutMapping("/proof-types/{id}")
    public Map<String, Object> updateProofType(@PathVariable String id,
                                                @RequestBody Map<String, Object> body) {
        return toProofTypeDto(service.updateProofType(id, body));
    }

    @PatchMapping("/proof-types/{id}/toggle")
    public ResponseEntity<Void> toggleProofType(@PathVariable String id) {
        service.toggleProofType(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/proof-types/{id}")
    public ResponseEntity<Void> deleteProofType(@PathVariable String id) {
        service.deleteProofType(id);
        return ResponseEntity.noContent().build();
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public List<Map<String, Object>> getUsers() {
        return service.getAllUsers().stream().map(this::toUserDto).toList();
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> body) {
        return toUserDto(service.createUser(body));
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return toUserDto(service.updateUser(id, body));
    }

    @PatchMapping("/users/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        service.resetPassword(id, body.getOrDefault("password", "changeme"));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/toggle")
    public ResponseEntity<Void> toggleUser(@PathVariable String id) {
        service.toggleUser(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        service.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    private Map<String, Object> toInstructionTypeDto(InstructionType t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("label", t.getLabel());
        m.put("categorie", t.getCategorie().name());
        m.put("urgenceDefaut", t.getUrgenceDefaut().name());
        m.put("livrableAttendu", t.getLivrableAttendu().name());
        m.put("actif", t.isActif());
        List<?> docs = List.of();
        String docsJson = t.getDocumentsAttendus();
        if (docsJson != null && !docsJson.isBlank()) {
            try { docs = objectMapper.readValue(docsJson, List.class); } catch (Exception ignored) {}
        }
        m.put("documentsAttendus", docs);
        return m;
    }

    private Map<String, Object> toStepDto(WorkflowStep s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("stepOrder", s.getStepOrder());
        m.put("stepLabel", s.getStepLabel());
        m.put("requiresSignature", s.isRequiresSignature());
        m.put("requiresAttachment", s.isRequiresAttachment());
        m.put("timeoutJours", s.getTimeoutJours());
        m.put("actorInstructions", s.getActorInstructions());
        if (s.getRequiredPoste() != null) {
            m.put("requiredPosteId", s.getRequiredPoste().getId());
            m.put("requiredPosteLibelle", s.getRequiredPoste().getLibelle());
        } else {
            m.put("requiredPosteId", null);
            m.put("requiredPosteLibelle", null);
        }
        return m;
    }

    private Map<String, Object> toPosteDto(Poste p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("code", p.getCode());
        m.put("libelle", p.getLibelle());
        m.put("actif", p.isActif());
        m.put("habilitations", p.getHabilitations().stream()
            .map(Enum::name)
            .collect(Collectors.toList()));
        return m;
    }

    @SuppressWarnings("deprecation")
    private Map<String, Object> toUserDto(AppUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nomComplet", u.getNomComplet());
        m.put("role", u.getRole() != null ? u.getRole().name() : null);
        m.put("actif", u.isActif());
        if (u.getPoste() != null) {
            m.put("posteId", u.getPoste().getId());
            m.put("posteLibelle", u.getPoste().getLibelle());
        } else {
            m.put("posteId", null);
            m.put("posteLibelle", null);
        }
        return m;
    }

    private Map<String, Object> toProofTypeDto(ProofType p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("label", p.getLabel());
        m.put("acceptedFormats", p.getAcceptedFormats());
        m.put("description", p.getDescription());
        m.put("actif", p.isActif());
        return m;
    }
}
