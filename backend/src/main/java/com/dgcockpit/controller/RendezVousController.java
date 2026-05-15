package com.dgcockpit.controller;

import com.dgcockpit.entity.RendezVous;
import com.dgcockpit.repository.RendezVousRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rendez-vous")
public class RendezVousController {

    private final RendezVousRepository repo;

    public RendezVousController(RendezVousRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Map<String, Object>> getAll() {
        return repo.findAllByOrderByHeureAsc().stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        return repo.findById(id)
            .map(r -> ResponseEntity.ok(toDto(r)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        return repo.findById(id).map(r -> {
            if (body.containsKey("statut")) {
                r.setStatut(RendezVous.Statut.valueOf(body.get("statut")));
            }
            return ResponseEntity.ok(toDto(repo.save(r)));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toDto(RendezVous r) {
        var map = new HashMap<String, Object>();
        map.put("id", r.getId());
        map.put("titre", r.getTitre());
        map.put("visiteur", r.getVisiteur());
        map.put("organisation", r.getOrganisation());
        map.put("contact", r.getContact());
        map.put("objet", r.getObjet());
        map.put("heure", r.getHeure());
        map.put("duree", r.getDuree());
        map.put("date", r.getDate() != null ? r.getDate().toString() : null);
        map.put("statut", r.getStatut().name());
        return map;
    }
}
