package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "instructions")
public class Instruction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String title;
    private String type;
    private String agentDisplay;

    @Enumerated(EnumType.STRING)
    private StatutInstruction statut = StatutInstruction.OUVERT;

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "instruction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sentAt ASC")
    private List<InstructionMessage> messages = new ArrayList<>();

    @OneToMany(mappedBy = "instruction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Assignee> assignees = new ArrayList<>();

    public enum StatutInstruction { OUVERT, EN_COURS, EN_ATTENTE, SOUMIS_VALIDATION, CLOTURE, REFUSE }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getAgentDisplay() { return agentDisplay; }
    public void setAgentDisplay(String agentDisplay) { this.agentDisplay = agentDisplay; }
    public StatutInstruction getStatut() { return statut; }
    public void setStatut(StatutInstruction statut) { this.statut = statut; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<InstructionMessage> getMessages() { return messages; }
    public List<Assignee> getAssignees() { return assignees; }
}
