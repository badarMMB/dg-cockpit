package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "courriers_depart")
public class CourrierDepart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String objet;
    private String destinataire;
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String contenu;

    @Column(columnDefinition = "TEXT")
    private String apercu;

    private String pieceJointe;
    private LocalDate dateEnvoi = LocalDate.now();

    @Enumerated(EnumType.STRING)
    private Statut statut = Statut.BROUILLON;

    public enum Statut { BROUILLON, SIGNE, EXPEDIE }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObjet() { return objet; }
    public void setObjet(String objet) { this.objet = objet; }
    public String getDestinataire() { return destinataire; }
    public void setDestinataire(String destinataire) { this.destinataire = destinataire; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public String getApercu() { return apercu; }
    public void setApercu(String apercu) { this.apercu = apercu; }
    public String getPieceJointe() { return pieceJointe; }
    public void setPieceJointe(String pieceJointe) { this.pieceJointe = pieceJointe; }
    public LocalDate getDateEnvoi() { return dateEnvoi; }
    public void setDateEnvoi(LocalDate dateEnvoi) { this.dateEnvoi = dateEnvoi; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
}
