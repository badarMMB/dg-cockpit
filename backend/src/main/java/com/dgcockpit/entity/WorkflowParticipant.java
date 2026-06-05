package com.dgcockpit.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "workflow_participants", indexes = {
    @Index(name = "idx_workflow_participants_step", columnList = "workflow_step_id"),
    @Index(name = "idx_workflow_participants_poste", columnList = "poste_id")
})
public class WorkflowParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workflow_step_id", nullable = false)
    private String workflowStepId;

    @Column(name = "poste_id", nullable = false)
    private String posteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleParticipant roleParticipant;

    @Column(columnDefinition = "boolean DEFAULT true")
    private boolean obligatoire = true;

    @Column(columnDefinition = "int DEFAULT 0")
    private int ordre = 0;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkflowStepId() { return workflowStepId; }
    public void setWorkflowStepId(String workflowStepId) { this.workflowStepId = workflowStepId; }
    public String getPosteId() { return posteId; }
    public void setPosteId(String posteId) { this.posteId = posteId; }
    public RoleParticipant getRoleParticipant() { return roleParticipant; }
    public void setRoleParticipant(RoleParticipant roleParticipant) { this.roleParticipant = roleParticipant; }
    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean obligatoire) { this.obligatoire = obligatoire; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
}
