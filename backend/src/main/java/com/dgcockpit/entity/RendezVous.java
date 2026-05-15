package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "rendez_vous")
public class RendezVous {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String titre;
    private String visiteur;
    private String organisation;
    private String contact;

    @Column(columnDefinition = "TEXT")
    private String objet;

    private String heure;
    private String duree;
    private LocalDate date = LocalDate.now();

    @Enumerated(EnumType.STRING)
    private Statut statut = Statut.PLANIFIE;

    public enum Statut { PLANIFIE, EN_COURS, TERMINE, ANNULE }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getVisiteur() { return visiteur; }
    public void setVisiteur(String visiteur) { this.visiteur = visiteur; }
    public String getOrganisation() { return organisation; }
    public void setOrganisation(String organisation) { this.organisation = organisation; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getObjet() { return objet; }
    public void setObjet(String objet) { this.objet = objet; }
    public String getHeure() { return heure; }
    public void setHeure(String heure) { this.heure = heure; }
    public String getDuree() { return duree; }
    public void setDuree(String duree) { this.duree = duree; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
}
