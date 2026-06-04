package com.dgcockpit.controller;

import com.dgcockpit.entity.*;
import com.dgcockpit.service.ParametresService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public Map<String, Object> createInstructionType(@RequestBody Map<String, Object> body) {
        return toInstructionTypeDto(service.createInstructionType(body));
    }

    @PutMapping("/instruction-types/{id}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public Map<String, Object> updateInstructionType(@PathVariable String id,
                                                      @RequestBody Map<String, Object> body) {
        return toInstructionTypeDto(service.updateInstructionType(id, body));
    }

    @PatchMapping("/instruction-types/{id}/toggle")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Void> toggleInstructionType(@PathVariable String id) {
        service.toggleInstructionType(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/instruction-types/{id}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Void> deleteInstructionType(@PathVariable String id) {
        service.deleteInstructionType(id);
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
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public Map<String, Object> createPoste(@RequestBody Map<String, Object> body) {
        return toPosteDto(service.createPoste(body));
    }

    @PutMapping("/postes/{id}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public Map<String, Object> updatePoste(@PathVariable String id,
                                            @RequestBody Map<String, Object> body) {
        return toPosteDto(service.updatePoste(id, body));
    }

    @PatchMapping("/postes/{id}/toggle")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Void> togglePoste(@PathVariable String id) {
        service.togglePoste(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/postes/{id}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Void> deletePoste(@PathVariable String id) {
        service.deletePoste(id);
        return ResponseEntity.noContent().build();
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public List<Map<String, Object>> getUsers() {
        return service.getAllUsers().stream().map(this::toUserDto).toList();
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('CAN_MANAGE_USERS')")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> body) {
        return toUserDto(service.createUser(body));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_USERS')")
    public Map<String, Object> updateUser(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return toUserDto(service.updateUser(id, body));
    }

    @PatchMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('CAN_MANAGE_USERS')")
    public ResponseEntity<Void> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        service.resetPassword(id, body.getOrDefault("password", "changeme"));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/toggle")
    @PreAuthorize("hasAuthority('CAN_MANAGE_USERS')")
    public ResponseEntity<Void> toggleUser(@PathVariable String id) {
        service.toggleUser(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_USERS')")
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
        m.put("typeInstruction", t.getTypeInstruction().name());
        m.put("typeDocumentAttenduId", t.getTypeDocumentAttenduId());
        m.put("actif", t.isActif());
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
        if (u.getManager() != null) {
            m.put("managerId", u.getManager().getId());
            m.put("managerNomComplet", u.getManager().getNomComplet());
        } else {
            m.put("managerId", null);
            m.put("managerNomComplet", null);
        }
        return m;
    }

}
