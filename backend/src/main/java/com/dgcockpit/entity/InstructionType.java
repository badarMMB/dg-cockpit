package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Enumerated(EnumType.STRING)
    private TypeLivrable livrableAttendu = TypeLivrable.CONFIRMATION;

    @Column(columnDefinition = "TEXT")
    private String documentsAttendus;

    private boolean actif = true;

    private final LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Étapes du circuit de validation associées à ce type d'instruction.
     * L'ensemble de ces étapes définit le workflow complet à suivre.
     * Triées par {@link WorkflowStep#getStepOrder()} croissant.
     */
    @OneToMany(mappedBy = "instructionType", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<WorkflowStep> workflowSteps = new ArrayList<>();

    public enum Categorie { STRATEGIQUE, OPERATIONNELLE, MANAGERIALE, JURIDIQUE }
    public enum Urgence { URGENT, NORMAL, PLANIFIE }
    public enum TypeLivrable { CONFIRMATION, PREUVE, DOCUMENT }

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
    public TypeLivrable getLivrableAttendu() { return livrableAttendu; }
    public void setLivrableAttendu(TypeLivrable livrableAttendu) { this.livrableAttendu = livrableAttendu; }
    public String getDocumentsAttendus() { return documentsAttendus; }
    public void setDocumentsAttendus(String documentsAttendus) { this.documentsAttendus = documentsAttendus; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<WorkflowStep> getWorkflowSteps() { return workflowSteps; }
    public void setWorkflowSteps(List<WorkflowStep> workflowSteps) { this.workflowSteps = workflowSteps; }
}
