package com.dgcockpit.controller;

import com.dgcockpit.repository.AuditLogRepository;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository repo;

    public AuditController(AuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Map<String, Object>> getRecent() {
        return repo.findTop50ByOrderByCreatedAtDesc().stream().<Map<String, Object>>map(a -> {
            var m = new HashMap<String, Object>();
            m.put("id",         a.getId());
            m.put("action",     a.getAction());
            m.put("entityType", a.getEntityType());
            m.put("entityId",   a.getEntityId());
            m.put("acteur",     a.getActeur());
            m.put("details",    a.getDetails());
            m.put("createdAt",  a.getCreatedAt().toString());
            return m;
        }).toList();
    }

    @GetMapping("/{entityType}/{entityId}")
    public List<Map<String, Object>> getByEntity(@PathVariable String entityType, @PathVariable String entityId) {
        return repo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId).stream().<Map<String, Object>>map(a -> {
            var m = new HashMap<String, Object>();
            m.put("id",        a.getId());
            m.put("action",    a.getAction());
            m.put("acteur",    a.getActeur());
            m.put("details",   a.getDetails());
            m.put("createdAt", a.getCreatedAt().toString());
            return m;
        }).toList();
    }
}
