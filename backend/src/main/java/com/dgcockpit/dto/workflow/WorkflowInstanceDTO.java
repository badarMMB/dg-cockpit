package com.dgcockpit.dto.workflow;

import com.dgcockpit.entity.WorkflowStatus;
import java.time.LocalDateTime;

public class WorkflowInstanceDTO {

    private String id;
    private String workflowDefinitionId;
    private String workflowLibelle;
    private String currentStepId;
    private String currentStepLibelle;
    private WorkflowStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String sourceDocumentId;
    private String sourceInstructionId;
    private String createdById;
    private String createdByNom;

    public WorkflowInstanceDTO() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkflowDefinitionId() { return workflowDefinitionId; }
    public void setWorkflowDefinitionId(String workflowDefinitionId) { this.workflowDefinitionId = workflowDefinitionId; }
    public String getWorkflowLibelle() { return workflowLibelle; }
    public void setWorkflowLibelle(String workflowLibelle) { this.workflowLibelle = workflowLibelle; }
    public String getCurrentStepId() { return currentStepId; }
    public void setCurrentStepId(String currentStepId) { this.currentStepId = currentStepId; }
    public String getCurrentStepLibelle() { return currentStepLibelle; }
    public void setCurrentStepLibelle(String currentStepLibelle) { this.currentStepLibelle = currentStepLibelle; }
    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getSourceDocumentId() { return sourceDocumentId; }
    public void setSourceDocumentId(String sourceDocumentId) { this.sourceDocumentId = sourceDocumentId; }
    public String getSourceInstructionId() { return sourceInstructionId; }
    public void setSourceInstructionId(String sourceInstructionId) { this.sourceInstructionId = sourceInstructionId; }
    public String getCreatedById() { return createdById; }
    public void setCreatedById(String createdById) { this.createdById = createdById; }
    public String getCreatedByNom() { return createdByNom; }
    public void setCreatedByNom(String createdByNom) { this.createdByNom = createdByNom; }
}
