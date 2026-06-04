package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "instruction_types")
public class InstructionType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categorie categorie;

    @Enumerated(EnumType.STRING)
    private Urgence urgenceDefaut = Urgence.NORMAL;

    /**
     * LIBRE  : pas de document attendu, clôture manuelle par l'initiateur.
     * DOCUMENTAIRE : un document de type {@link #typeDocumentAttenduId} doit être produit ;
     *                les événements du circuit sont reportés dans le fil ; clôture auto.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeInstruction typeInstruction = TypeInstruction.LIBRE;

    /** FK vers TypeDocument — uniquement pour typeInstruction = DOCUMENTAIRE */
    private String typeDocumentAttenduId;

    private boolean actif = true;

    private final LocalDateTime createdAt = LocalDateTime.now();

    public enum Categorie { STRATEGIQUE, OPERATIONNELLE, MANAGERIALE, JURIDIQUE }
    public enum Urgence { URGENT, NORMAL, PLANIFIE }
    public enum TypeInstruction { LIBRE, DOCUMENTAIRE }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Categorie getCategorie() { return categorie; }
    public void setCategorie(Categorie categorie) { this.categorie = categorie; }
    public Urgence getUrgenceDefaut() { return urgenceDefaut; }
    public void setUrgenceDefaut(Urgence urgenceDefaut) { this.urgenceDefaut = urgenceDefaut; }
    public TypeInstruction getTypeInstruction() { return typeInstruction; }
    public void setTypeInstruction(TypeInstruction typeInstruction) { this.typeInstruction = typeInstruction; }
    public String getTypeDocumentAttenduId() { return typeDocumentAttenduId; }
    public void setTypeDocumentAttenduId(String typeDocumentAttenduId) { this.typeDocumentAttenduId = typeDocumentAttenduId; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
