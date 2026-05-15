package com.dgcockpit.controller;

import com.dgcockpit.entity.CourrierDepart;
import com.dgcockpit.repository.CourrierDepartRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courriers-depart")
public class CourrierDepartController {

    private final CourrierDepartRepository repo;

    public CourrierDepartController(CourrierDepartRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Map<String, Object>> getAll() {
        return repo.findAllByOrderByDateEnvoiDesc().stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        return repo.findById(id)
            .map(c -> ResponseEntity.ok(toDto(c)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        return repo.findById(id).map(c -> {
            if (body.containsKey("statut")) {
                c.setStatut(CourrierDepart.Statut.valueOf(body.get("statut")));
            }
            return ResponseEntity.ok(toDto(repo.save(c)));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toDto(CourrierDepart c) {
        var map = new java.util.HashMap<String, Object>();
        map.put("id", c.getId());
        map.put("objet", c.getObjet());
        map.put("destinataire", c.getDestinataire());
        map.put("reference", c.getReference());
        map.put("contenu", c.getContenu());
        map.put("apercu", c.getApercu());
        map.put("pieceJointe", c.getPieceJointe());
        map.put("dateEnvoi", c.getDateEnvoi() != null ? c.getDateEnvoi().toString() : null);
        map.put("statut", c.getStatut().name());
        return map;
    }
}
