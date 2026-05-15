package com.dgcockpit.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "templates_courrier_depart")
public class TemplateCourrierDepart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String nom;
    private String type;

    private String objet;

    @Column(columnDefinition = "TEXT")
    private String contenu;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getObjet() { return objet; }
    public void setObjet(String objet) { this.objet = objet; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
}
