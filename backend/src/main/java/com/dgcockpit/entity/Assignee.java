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

    /** ID de l'utilisateur assigné */
    private String userId;

    /** Nom complet dénormalisé pour l'affichage */
    private String agent;

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instruction getInstruction() { return instruction; }
    public void setInstruction(Instruction instruction) { this.instruction = instruction; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
}
