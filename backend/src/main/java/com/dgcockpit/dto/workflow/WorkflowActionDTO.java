package com.dgcockpit.dto.workflow;

import com.dgcockpit.entity.WorkflowActionType;
import java.time.LocalDateTime;

public class WorkflowActionDTO {

    private String id;
    private String workflowInstanceId;
    private String stepId;
    private String stepLibelle;
    private String userId;
    private String userNom;
    private WorkflowActionType action;
    private String commentaire;
    private LocalDateTime createdAt;

    public WorkflowActionDTO() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    public String getStepLibelle() { return stepLibelle; }
    public void setStepLibelle(String stepLibelle) { this.stepLibelle = stepLibelle; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserNom() { return userNom; }
    public void setUserNom(String userNom) { this.userNom = userNom; }
    public WorkflowActionType getAction() { return action; }
    public void setAction(WorkflowActionType action) { this.action = action; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
