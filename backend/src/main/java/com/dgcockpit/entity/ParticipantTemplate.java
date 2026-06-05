package com.dgcockpit.entity;

import jakarta.persistence.*;

/**
 * Template de participant attaché à un InstructionType.
 *
 * Définit une règle de pré-affectation par Poste :
 * "Pour les instructions de ce type, les utilisateurs ayant le Poste X jouent le rôle Y".
 *
 * Exemples :
 *   InstructionType "Compte Rendu de Mission"
 *     → Poste "Chef Mission"  : RAPPORTEUR  (obligatoire)
 *     → Poste "Travailleur"   : PARTICIPANT (facultatif, tous les actifs du poste)
 *
 * Résolution au runtime dans BureauController.creerInstructionBottomUp() :
 *   RAPPORTEUR  → premier utilisateur actif avec le Poste indiqué
 *   PARTICIPANT → tous les utilisateurs actifs avec le Poste indiqué
 *   Fallback si obligatoire et aucun candidat : manager du créateur
 */
@Entity
@Table(name = "participant_templates", indexes = {
    @Index(name = "idx_pt_instruction_type", columnList = "instruction_type_id")
})
public class ParticipantTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "instruction_type_id", nullable = false)
    private String instructionTypeId;

    @Column(name = "poste_id", nullable = false)
    private String posteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleParticipant role;

    /** Si true et aucun candidat trouvé → fallback sur manager du créateur. */
    @Column(columnDefinition = "boolean DEFAULT true")
    private boolean obligatoire = true;

    /** Ordre d'affichage / résolution (0 = premier). */
    @Column(columnDefinition = "int DEFAULT 0")
    private int ordre = 0;

    // ── Getters / Setters ────────────────────────────────────────────────

    public String getId() { return id; }
    public String getInstructionTypeId() { return instructionTypeId; }
    public void setInstructionTypeId(String v) { this.instructionTypeId = v; }
    public String getPosteId() { return posteId; }
    public void setPosteId(String v) { this.posteId = v; }
    public RoleParticipant getRole() { return role; }
    public void setRole(RoleParticipant v) { this.role = v; }
    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean v) { this.obligatoire = v; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int v) { this.ordre = v; }
}
