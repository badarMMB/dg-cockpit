package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
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
    private String type;         // kept for search compat — mirrors instructionType.label
    private String agentDisplay; // kept for search compat

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "instruction_type_id")
    private InstructionType instructionType;

    private String urgence; // URGENT | NORMAL | PLANIFIE

    @Column(columnDefinition = "boolean DEFAULT false")
    private boolean confidentialite = false;

    private LocalDate echeance;

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

    public InstructionType getInstructionType() { return instructionType; }
    public void setInstructionType(InstructionType instructionType) { this.instructionType = instructionType; }
    public String getUrgence() { return urgence; }
    public void setUrgence(String urgence) { this.urgence = urgence; }
    public boolean isConfidentialite() { return confidentialite; }
    public void setConfidentialite(boolean confidentialite) { this.confidentialite = confidentialite; }
    public LocalDate getEcheance() { return echeance; }
    public void setEcheance(LocalDate echeance) { this.echeance = echeance; }
}
