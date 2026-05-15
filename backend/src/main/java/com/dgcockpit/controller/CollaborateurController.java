package com.dgcockpit.controller;

import com.dgcockpit.entity.Collaborateur;
import com.dgcockpit.repository.CollaborateurRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/collaborateurs")
public class CollaborateurController {

    private final CollaborateurRepository repo;

    public CollaborateurController(CollaborateurRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Map<String, Object>> getAll() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Collaborateur c = new Collaborateur();
        c.setName((String) body.get("name"));
        c.setEmail((String) body.get("email"));
        c.setRole((String) body.getOrDefault("role", "Agent (Chef de Service)"));
        return toDto(repo.save(c));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toDto(Collaborateur c) {
        var map = new HashMap<String, Object>();
        map.put("id", c.getId());
        map.put("name", Objects.requireNonNullElse(c.getName(), ""));
        map.put("email", Objects.requireNonNullElse(c.getEmail(), ""));
        map.put("role", Objects.requireNonNullElse(c.getRole(), ""));
        map.put("status", c.getStatut().name());
        return map;
    }
}
