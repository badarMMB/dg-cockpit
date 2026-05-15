package com.dgcockpit.service;

import com.dgcockpit.entity.AuditLog;
import com.dgcockpit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(String action, String entityType, String entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId != null ? entityId : "");
        entry.setActeur("Admin DG");
        entry.setDetails(details != null ? details : "");
        repo.save(entry);
    }
}
