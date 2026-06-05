package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_actions", indexes = {
    @Index(name = "idx_workflow_actions_instance", columnList = "workflow_instance_id"),
    @Index(name = "idx_workflow_actions_step", columnList = "step_id")
})
public class WorkflowAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workflow_instance_id", nullable = false)
    private String workflowInstanceId;

    @Column(name = "step_id")
    private String stepId;

    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowActionType action;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    private LocalDateTime createdAt = LocalDateTime.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public WorkflowActionType getAction() { return action; }
    public void setAction(WorkflowActionType action) { this.action = action; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
