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

    // ── Statut global du circuit (nouveau modèle) ─────────────────────────────

    /**
     * Statut de haut niveau du dossier dans le moteur de workflow.
     * Remplace progressivement {@link #statut}.
     */
    @Enumerated(EnumType.STRING)
    private GlobalStatus globalStatus = GlobalStatus.BROUILLON;

    /**
     * Étape courante du circuit de validation.
     * Null si le dossier est en BROUILLON ou CLÔTURÉ.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_step_id")
    private WorkflowStep currentStep;

    /**
     * Utilisateur actuellement en charge de l'étape courante.
     * Alimenté automatiquement par le WorkflowService lors du passage d'étape.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_actor_id")
    private AppUser currentActor;

    // ── Statut simplifié (modèle hérité, déprécié) ───────────────────────────

    /**
     * @deprecated Remplacé par {@link #globalStatus} + {@link #currentStep}.
     * Conservé pour la compatibilité des contrôleurs existants
     * jusqu'à la finalisation de la migration (Étape 2).
     */
    @Deprecated(since = "workflow-engine-v2")
    @SuppressWarnings({"java:S1133", "java:S1874"})
    @Enumerated(EnumType.STRING)
    private StatutInstruction statut = StatutInstruction.OUVERT;

    private final LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "instruction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sentAt ASC")
    private List<InstructionMessage> messages = new ArrayList<>();

    @OneToMany(mappedBy = "instruction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Assignee> assignees = new ArrayList<>();

    // ── Enums ─────────────────────────────────────────────────────────────────

    /** Statut de haut niveau — utilisé par le nouveau moteur de workflow */
    public enum GlobalStatus {
        /** Dossier créé mais circuit pas encore lancé */
        BROUILLON,
        /** Circuit en cours, en attente d'action sur une étape */
        EN_CIRCUIT,
        /** Toutes les étapes ont été validées : dossier clôturé positivement */
        CLOTURE_VALIDE,
        /** Un acteur a rejeté son étape de façon définitive */
        CLOTURE_REJETE
    }

    /** @deprecated Utiliser {@link GlobalStatus}. */
    @Deprecated(since = "workflow-engine-v2")
    @SuppressWarnings("java:S1133")
    public enum StatutInstruction {
        OUVERT, EN_COURS, EN_ATTENTE, SOUMIS_VALIDATION, CLOTURE, REFUSE
    }

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

    public GlobalStatus getGlobalStatus() { return globalStatus; }
    public void setGlobalStatus(GlobalStatus globalStatus) { this.globalStatus = globalStatus; }

    public WorkflowStep getCurrentStep() { return currentStep; }
    public void setCurrentStep(WorkflowStep currentStep) { this.currentStep = currentStep; }

    public AppUser getCurrentActor() { return currentActor; }
    public void setCurrentActor(AppUser currentActor) { this.currentActor = currentActor; }

    /** @deprecated Utiliser {@link #getGlobalStatus()} */
    @Deprecated(since = "workflow-engine-v2", forRemoval = true)
    public StatutInstruction getStatut() { return statut; }

    /** @deprecated Utiliser {@link #setGlobalStatus(GlobalStatus)} */
    @Deprecated(since = "workflow-engine-v2", forRemoval = true)
    public void setStatut(StatutInstruction statut) { this.statut = statut; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<InstructionMessage> getMessages() { return messages; }
    public List<Assignee> getAssignees() { return assignees; }
}
