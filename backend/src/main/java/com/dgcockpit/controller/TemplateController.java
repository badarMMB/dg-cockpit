package com.dgcockpit.controller;

import com.dgcockpit.repository.TemplateCourrierDepartRepository;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates-courrier")
public class TemplateController {

    private final TemplateCourrierDepartRepository repo;

    public TemplateController(TemplateCourrierDepartRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Map<String, Object>> getAll() {
        return repo.findAllByOrderByNomAsc().stream().<Map<String, Object>>map(t -> {
            var m = new HashMap<String, Object>();
            m.put("id",      t.getId());
            m.put("nom",     t.getNom());
            m.put("type",    t.getType());
            m.put("objet",   t.getObjet() != null ? t.getObjet() : "");
            m.put("contenu", t.getContenu() != null ? t.getContenu() : "");
            return m;
        }).toList();
    }
}
