package com.dgcockpit.dto.workflow;

import com.dgcockpit.entity.RoleParticipant;

public class WorkflowParticipantDTO {

    private String id;
    private String workflowStepId;
    private String posteId;
    private String posteLibelle;
    private RoleParticipant roleParticipant;
    private boolean obligatoire;
    private int ordre;

    public WorkflowParticipantDTO() {}

    public WorkflowParticipantDTO(String id, String workflowStepId, String posteId,
                                   String posteLibelle, RoleParticipant roleParticipant,
                                   boolean obligatoire, int ordre) {
        this.id = id;
        this.workflowStepId = workflowStepId;
        this.posteId = posteId;
        this.posteLibelle = posteLibelle;
        this.roleParticipant = roleParticipant;
        this.obligatoire = obligatoire;
        this.ordre = ordre;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkflowStepId() { return workflowStepId; }
    public void setWorkflowStepId(String workflowStepId) { this.workflowStepId = workflowStepId; }
    public String getPosteId() { return posteId; }
    public void setPosteId(String posteId) { this.posteId = posteId; }
    public String getPosteLibelle() { return posteLibelle; }
    public void setPosteLibelle(String posteLibelle) { this.posteLibelle = posteLibelle; }
    public RoleParticipant getRoleParticipant() { return roleParticipant; }
    public void setRoleParticipant(RoleParticipant roleParticipant) { this.roleParticipant = roleParticipant; }
    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean obligatoire) { this.obligatoire = obligatoire; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
}
