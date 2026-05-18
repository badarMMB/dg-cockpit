package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.InstructionType;
import com.dgcockpit.entity.ProofType;
import com.dgcockpit.service.ParametresService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parametres")
public class ParametresController {

    private final ParametresService service;

    public ParametresController(ParametresService service) {
        this.service = service;
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
        return m;
    }

    private Map<String, Object> toUserDto(AppUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nomComplet", u.getNomComplet());
        m.put("role", u.getRole().name());
        m.put("actif", u.isActif());
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
