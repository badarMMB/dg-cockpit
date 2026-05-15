package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "validation_steps")
public class ValidationStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instruction_id", nullable = false)
    private Instruction instruction;

    @Enumerated(EnumType.STRING)
    private Statut statut;

    private String validateur;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    private LocalDateTime date = LocalDateTime.now();

    public enum Statut { SOUMIS, VALIDE, REJETE }

    public String getId() { return id; }
    public Instruction getInstruction() { return instruction; }
    public void setInstruction(Instruction instruction) { this.instruction = instruction; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public String getValidateur() { return validateur; }
    public void setValidateur(String validateur) { this.validateur = validateur; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public LocalDateTime getDate() { return date; }
}
