package com.dgcockpit.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "assignees")
public class Assignee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instruction_id", nullable = false)
    private Instruction instruction;

    private String agent;

    @Enumerated(EnumType.STRING)
    private RoleAssignee role;

    private boolean hasRequestedClosure = false;

    public enum RoleAssignee {
        ACTION_SIGNATURE, ACTION_COURRIER, ACTION_NOTE, AVIS_SIMPLE
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instruction getInstruction() { return instruction; }
    public void setInstruction(Instruction instruction) { this.instruction = instruction; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public RoleAssignee getRole() { return role; }
    public void setRole(RoleAssignee role) { this.role = role; }
    public boolean isHasRequestedClosure() { return hasRequestedClosure; }
    public void setHasRequestedClosure(boolean hasRequestedClosure) { this.hasRequestedClosure = hasRequestedClosure; }
}
