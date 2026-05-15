package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "courriers_arrive")
public class CourrierArrive {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String objet;
    private String expediteur;
    private String reference;
    private boolean urgent = false;

    @Column(columnDefinition = "TEXT")
    private String contenu;

    @Column(columnDefinition = "TEXT")
    private String apercu;

    private String pieceJointe;
    private LocalDate dateReception = LocalDate.now();

    @Enumerated(EnumType.STRING)
    private Statut statut = Statut.NON_TRAITE;

    public enum Statut { NON_TRAITE, EN_COURS, ARCHIVE }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObjet() { return objet; }
    public void setObjet(String objet) { this.objet = objet; }
    public String getExpediteur() { return expediteur; }
    public void setExpediteur(String expediteur) { this.expediteur = expediteur; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public boolean isUrgent() { return urgent; }
    public void setUrgent(boolean urgent) { this.urgent = urgent; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public String getApercu() { return apercu; }
    public void setApercu(String apercu) { this.apercu = apercu; }
    public String getPieceJointe() { return pieceJointe; }
    public void setPieceJointe(String pieceJointe) { this.pieceJointe = pieceJointe; }
    public LocalDate getDateReception() { return dateReception; }
    public void setDateReception(LocalDate dateReception) { this.dateReception = dateReception; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
}
