package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "classeurs")
public class Classeur {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String nom;

    @Enumerated(EnumType.STRING)
    private Type type = Type.LIBRE;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String couleur = "#3B82F6";

    // true = créé automatiquement par le système, non supprimable
    @Column(nullable = false)
    private boolean systeme = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Type { MENSUEL, ANNUEL, DESTINATAIRE, NATURE, COURRIER_ARRIVE, COURRIER_DEPART, LIBRE }

    public String getId() { return id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCouleur() { return couleur; }
    public void setCouleur(String couleur) { this.couleur = couleur; }
    public boolean isSysteme() { return systeme; }
    public void setSysteme(boolean systeme) { this.systeme = systeme; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
