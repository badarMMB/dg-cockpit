package com.dgcockpit.entity;

import jakarta.persistence.*;

/**
 * Définit une étape dans le circuit de validation d'un type d'instruction.
 * Plusieurs WorkflowStep liés au même {@link InstructionType} forment le circuit complet.
 * <p>
 * Exemple de circuit "Courrier Départ" :
 *   stepOrder=0 → Rédaction (CHEF_SERVICE, pas de signature)
 *   stepOrder=1 → Validation hiérarchique (DIRECTEUR, signature requise)
 *   stepOrder=2 → Enregistrement départ (SECRETAIRE_GEN, pas de signature)
 */
@Entity
@Table(name = "workflow_steps",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_workflow_step_order",
           columnNames = {"instruction_type_id", "step_order"}
       ))
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Type d'instruction auquel appartient ce circuit */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instruction_type_id", nullable = false)
    private InstructionType instructionType;

    /** Position de l'étape dans le circuit (commence à 0) */
    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    /** Libellé affiché dans l'UI, ex : "Validation par le Directeur" */
    @Column(nullable = false)
    private String stepLabel;

    /**
     * Poste requis pour agir sur cette étape.
     * Si null → tout utilisateur peut agir (étape ouverte, déconseillé en prod).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "required_poste_id")
    private Poste requiredPoste;

    /**
     * Indique si cette étape nécessite un appel à
     * {@link com.dgcockpit.service.DocumentFinalizationService}
     * pour apposer la signature/le cachet sur le document joint.
     */
    private boolean requiresSignature = false;

    /** Un document PDF joint est obligatoire pour valider cette étape */
    private boolean requiresAttachment = false;

    /**
     * Délai d'alerte en jours avant escalade automatique.
     * Null = pas de délai défini.
     */
    private Integer timeoutJours;

    /**
     * Instructions affichées à l'acteur lorsque c'est son tour d'agir.
     * Ex : "Veuillez relire et signer le projet de courrier ci-joint."
     */
    @Column(columnDefinition = "TEXT")
    private String actorInstructions;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public InstructionType getInstructionType() { return instructionType; }
    public void setInstructionType(InstructionType instructionType) { this.instructionType = instructionType; }

    public int getStepOrder() { return stepOrder; }
    public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }

    public String getStepLabel() { return stepLabel; }
    public void setStepLabel(String stepLabel) { this.stepLabel = stepLabel; }

    public Poste getRequiredPoste() { return requiredPoste; }
    public void setRequiredPoste(Poste requiredPoste) { this.requiredPoste = requiredPoste; }

    public boolean isRequiresSignature() { return requiresSignature; }
    public void setRequiresSignature(boolean requiresSignature) { this.requiresSignature = requiresSignature; }

    public boolean isRequiresAttachment() { return requiresAttachment; }
    public void setRequiresAttachment(boolean requiresAttachment) { this.requiresAttachment = requiresAttachment; }

    public Integer getTimeoutJours() { return timeoutJours; }
    public void setTimeoutJours(Integer timeoutJours) { this.timeoutJours = timeoutJours; }

    public String getActorInstructions() { return actorInstructions; }
    public void setActorInstructions(String actorInstructions) { this.actorInstructions = actorInstructions; }
}
