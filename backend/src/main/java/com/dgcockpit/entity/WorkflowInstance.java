package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_instances", indexes = {
    @Index(name = "idx_workflow_instances_definition", columnList = "workflow_definition_id"),
    @Index(name = "idx_workflow_instances_current_step", columnList = "current_step_id"),
    @Index(name = "idx_workflow_instances_created_by", columnList = "created_by_id")
})
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workflow_definition_id", nullable = false)
    private String workflowDefinitionId;

    @Column(name = "current_step_id")
    private String currentStepId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowStatus status = WorkflowStatus.RUNNING;

    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime completedAt;

    private String sourceDocumentId;
    private String sourceInstructionId;
    private String createdById;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkflowDefinitionId() { return workflowDefinitionId; }
    public void setWorkflowDefinitionId(String workflowDefinitionId) { this.workflowDefinitionId = workflowDefinitionId; }
    public String getCurrentStepId() { return currentStepId; }
    public void setCurrentStepId(String currentStepId) { this.currentStepId = currentStepId; }
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
}
