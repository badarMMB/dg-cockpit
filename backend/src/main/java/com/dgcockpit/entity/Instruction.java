package com.dgcockpit.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "instructions")
public class Instruction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String title;

    /** Conservé pour la compatibilité des requêtes de recherche — reflète instructionType.label */
    private String type;

    /** Conservé pour la compatibilité des requêtes de recherche */
    private String agentDisplay;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "instruction_type_id")
    private InstructionType instructionType;

    private String urgence; // URGENT | NORMAL | PLANIFIE

    @Column(columnDefinition = "boolean DEFAULT false")
    private boolean confidentialite = false;

    private LocalDate echeance;

    @Enumerated(EnumType.STRING)
    private StatutInstruction statut = StatutInstruction.OUVERT;

    /** ID de l'utilisateur qui a créé l'instruction */
    private String createdById;

    private final LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "instruction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sentAt ASC")
    private List<InstructionMessage> messages = new ArrayList<>();

    @OneToMany(mappedBy = "instruction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Assignee> assignees = new ArrayList<>();

    // ── Workflow Engine ──────────────────────────────────────────────────────
    // Liens vers le workflow si cette instruction est pilotée par un WorkflowInstance
    private String workflowInstanceId;
    private String workflowStepId;

    public enum StatutInstruction { OUVERT, EN_COURS, CLOTURE }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAgentDisplay() { return agentDisplay; }
    public void setAgentDisplay(String agentDisplay) { this.agentDisplay = agentDisplay; }

    public InstructionType getInstructionType() { return instructionType; }
    public void setInstructionType(InstructionType instructionType) { this.instructionType = instructionType; }

    public String getUrgence() { return urgence; }
    public void setUrgence(String urgence) { this.urgence = urgence; }

    public boolean isConfidentialite() { return confidentialite; }
    public void setConfidentialite(boolean confidentialite) { this.confidentialite = confidentialite; }

    public LocalDate getEcheance() { return echeance; }
    public void setEcheance(LocalDate echeance) { this.echeance = echeance; }

    public StatutInstruction getStatut() { return statut; }
    public void setStatut(StatutInstruction statut) { this.statut = statut; }

    public String getCreatedById() { return createdById; }
    public void setCreatedById(String createdById) { this.createdById = createdById; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<InstructionMessage> getMessages() { return messages; }
    public List<Assignee> getAssignees() { return assignees; }

    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }

    public String getWorkflowStepId() { return workflowStepId; }
    public void setWorkflowStepId(String workflowStepId) { this.workflowStepId = workflowStepId; }
}
