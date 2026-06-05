package com.dgcockpit.dto.workflow;

import java.time.LocalDateTime;

public class WorkflowDefinitionDTO {
    private String id;
    private String code;
    private String libelle;
    private String description;
    private boolean actif;
    private int version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public WorkflowDefinitionDTO() {}

    public WorkflowDefinitionDTO(String id, String code, String libelle, String description, 
                                 boolean actif, int version, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.code = code;
        this.libelle = libelle;
        this.description = description;
        this.actif = actif;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
