package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Représente une fonction / poste dans l'organigramme.
 * Remplace l'enum codé en dur {@link AppUser.Role} pour gérer dynamiquement
 * les droits métier via des habilitations.
 */
@Entity
@Table(name = "postes")
public class Poste {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Code technique unique, ex : "DG", "CHEF_SERVICE", "SECRETAIRE_GEN" */
    @Column(nullable = false, unique = true)
    private String code;

    /** Libellé affiché dans l'UI, ex : "Directeur Général" */
    @Column(nullable = false)
    private String libelle;

    /**
     * Ensemble des habilitations accordées à ce poste.
     * Stocké dans la table de jointure {@code poste_habilitations}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "poste_habilitations", joinColumns = @JoinColumn(name = "poste_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "habilitation")
    private Set<Habilitation> habilitations = new HashSet<>();

    /** Indique si ce poste est encore utilisable lors de la création de circuits */
    private boolean actif = true;

    // ── Enum des droits fonctionnels ──────────────────────────────────────────

    public enum Habilitation {
        /** Peut initier une nouvelle instruction / dossier */
        CAN_CREATE_INSTRUCTION,
        /** Peut apposer une signature électronique sur un document */
        CAN_SIGN,
        /** Peut valider une étape de workflow dont il est l'acteur requis */
        CAN_VALIDATE,
        /** Peut rejeter une étape de workflow */
        CAN_REJECT,
        /** Peut clôturer définitivement un dossier */
        CAN_CLOSE,
        /** Peut gérer les utilisateurs (Paramètres) */
        CAN_MANAGE_USERS,
        /** Peut gérer les types d'instructions et circuits (Paramètres) */
        CAN_MANAGE_TYPES,
        /** Peut consulter tous les dossiers (vue superviseur) */
        CAN_VIEW_ALL
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public Set<Habilitation> getHabilitations() { return habilitations; }
    public void setHabilitations(Set<Habilitation> habilitations) { this.habilitations = habilitations; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    /** Vérifie si ce poste possède une habilitation donnée */
    public boolean hasHabilitation(Habilitation h) {
        return habilitations.contains(h);
    }
}
