package com.dgcockpit.controller;

import com.dgcockpit.repository.*;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final InstructionRepository instructionRepo;
    private final CourrierArriveRepository courrierArriveRepo;
    private final CourrierDepartRepository courrierDepartRepo;
    private final RendezVousRepository rendezVousRepo;
    private final CollaborateurRepository collaborateurRepo;

    public SearchController(InstructionRepository instructionRepo,
                            CourrierArriveRepository courrierArriveRepo,
                            CourrierDepartRepository courrierDepartRepo,
                            RendezVousRepository rendezVousRepo,
                            CollaborateurRepository collaborateurRepo) {
        this.instructionRepo = instructionRepo;
        this.courrierArriveRepo = courrierArriveRepo;
        this.courrierDepartRepo = courrierDepartRepo;
        this.rendezVousRepo = rendezVousRepo;
        this.collaborateurRepo = collaborateurRepo;
    }

    @GetMapping
    public List<Map<String, Object>> search(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        String term = q.trim();
        List<Map<String, Object>> results = new ArrayList<>();

        instructionRepo.search(term).stream().limit(5).forEach(i -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("type", "instruction");
            m.put("id", i.getId());
            m.put("titre", i.getTitle() != null ? i.getTitle() : "");
            m.put("apercu", i.getType() != null ? i.getType() : "");
            m.put("route", "/chat");
            results.add(m);
        });

        courrierArriveRepo.search(term).stream().limit(5).forEach(c -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("type", "courrier-arrive");
            m.put("id", c.getId());
            m.put("titre", c.getObjet() != null ? c.getObjet() : "");
            m.put("apercu", c.getExpediteur() != null ? "De : " + c.getExpediteur() : "");
            m.put("route", "/inbox/" + c.getId());
            results.add(m);
        });

        courrierDepartRepo.search(term).stream().limit(5).forEach(c -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("type", "courrier-depart");
            m.put("id", c.getId());
            m.put("titre", c.getObjet() != null ? c.getObjet() : "");
            m.put("apercu", c.getDestinataire() != null ? "À : " + c.getDestinataire() : "");
            m.put("route", "/outbox/" + c.getId());
            results.add(m);
        });

        rendezVousRepo.search(term).stream().limit(5).forEach(r -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("type", "rendez-vous");
            m.put("id", r.getId());
            m.put("titre", r.getTitre() != null ? r.getTitre() : "");
            m.put("apercu", r.getVisiteur() != null ? r.getVisiteur() : "");
            m.put("route", "/appointments/" + r.getId());
            results.add(m);
        });

        collaborateurRepo.search(term).stream().limit(5).forEach(c -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("type", "collaborateur");
            m.put("id", c.getId());
            m.put("titre", c.getName() != null ? c.getName() : "");
            m.put("apercu", c.getRole() != null ? c.getRole() : "");
            m.put("route", "/settings");
            results.add(m);
        });

        return results;
    }
}
