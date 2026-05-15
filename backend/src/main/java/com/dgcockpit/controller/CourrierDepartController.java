package com.dgcockpit.controller;

import com.dgcockpit.entity.CourrierDepart;
import com.dgcockpit.repository.CourrierDepartRepository;
import com.dgcockpit.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courriers-depart")
public class CourrierDepartController {

    private final CourrierDepartRepository repo;
    private final AuditService auditService;

    public CourrierDepartController(CourrierDepartRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
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

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        CourrierDepart c = new CourrierDepart();
        if (body.containsKey("objet"))        c.setObjet(body.get("objet"));
        if (body.containsKey("destinataire")) c.setDestinataire(body.get("destinataire"));
        if (body.containsKey("reference"))    c.setReference(body.get("reference"));
        if (body.containsKey("contenu"))      c.setContenu(body.get("contenu"));
        if (body.containsKey("apercu"))       c.setApercu(body.get("apercu"));
        c.setStatut(CourrierDepart.Statut.BROUILLON);
        CourrierDepart saved = repo.save(c);
        auditService.log("CREER", "courrier-depart", saved.getId(),
            "Brouillon créé : " + saved.getObjet());
        return ResponseEntity.ok(toDto(saved));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        return repo.findById(id).map(c -> {
            if (body.containsKey("statut"))       c.setStatut(CourrierDepart.Statut.valueOf(body.get("statut")));
            if (body.containsKey("objet"))        c.setObjet(body.get("objet"));
            if (body.containsKey("destinataire")) c.setDestinataire(body.get("destinataire"));
            if (body.containsKey("contenu"))      c.setContenu(body.get("contenu"));
            if (body.containsKey("apercu"))       c.setApercu(body.get("apercu"));
            CourrierDepart saved = repo.save(c);
            String detail = body.containsKey("statut")
                ? "Statut mis à jour : " + body.get("statut")
                : "Contenu modifié : " + saved.getObjet();
            auditService.log("MODIFIER", "courrier-depart", id, detail);
            return ResponseEntity.ok(toDto(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toDto(CourrierDepart c) {
        var map = new HashMap<String, Object>();
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
