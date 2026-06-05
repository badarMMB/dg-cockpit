package com.dgcockpit.dto.workflow;

import java.time.LocalDateTime;

import com.dgcockpit.entity.StepType;

public class WorkflowStepDTO {
    private String id;
    private String workflowId;
    private int ordre;
    private String code;
    private String libelle;
    private String description;
    private StepType stepType;
    private boolean autoTransition;
    private String nextStepId;
    private String configJson; // JSON extensible pour paramètres spécifiques
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public WorkflowStepDTO() {}

    public WorkflowStepDTO(String id, String workflowId, int ordre, String code, String libelle,
                          String description, StepType stepType, boolean autoTransition, 
                          String nextStepId, String configJson, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.workflowId = workflowId;
        this.ordre = ordre;
        this.code = code;
        this.libelle = libelle;
        this.description = description;
        this.stepType = stepType;
        this.autoTransition = autoTransition;
        this.nextStepId = nextStepId;
        this.configJson = configJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public StepType getStepType() { return stepType; }
    public void setStepType(StepType stepType) { this.stepType = stepType; }

    public boolean isAutoTransition() { return autoTransition; }
    public void setAutoTransition(boolean autoTransition) { this.autoTransition = autoTransition; }

    public String getNextStepId() { return nextStepId; }
    public void setNextStepId(String nextStepId) { this.nextStepId = nextStepId; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
