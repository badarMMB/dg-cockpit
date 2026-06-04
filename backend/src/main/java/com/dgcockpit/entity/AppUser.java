package com.dgcockpit.entity;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "app_users")
public class AppUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String nomComplet;

    @Column(nullable = false)
    private String passwordHash;

    /**
     * @deprecated Remplacé par {@link #poste} pour une gestion dynamique des rôles.
     * Conservé pour la compatibilité descendante des contrôleurs existants
     * jusqu'à la finalisation de la migration (Étape 2).
     */
    @Deprecated
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private Role role;

    /**
     * Poste occupé par l'utilisateur dans l'organigramme.
     * Détermine les habilitations et les étapes de workflow auxquelles
     * cet utilisateur peut participer.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "poste_id")
    private Poste poste;

    /**
     * Responsable hiérarchique direct (auto-relation).
     * Permet de construire l'organigramme et de gérer les délégations.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private AppUser manager;

    /**
     * Subordonnés directs de cet utilisateur (inverse de {@link #manager}).
     * Non persisté en base (mappedBy), chargé uniquement si nécessaire.
     */
    @OneToMany(mappedBy = "manager", fetch = FetchType.LAZY)
    private List<AppUser> subordonnes = new ArrayList<>();

    /**
     * Rôles secondaires cumulatifs — accordés en plus du poste principal.
     * {@code SECRETAIRE} : accorde l'accès au bureau d'envoi de documents.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_secondary_roles",
                     joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role_secondaire")
    private Set<RoleSecondaire> rolesSecondaires = new HashSet<>();

    private boolean actif = true;

    private final LocalDateTime createdAt = LocalDateTime.now();

    // ── Enum de rôle (déprécié) ───────────────────────────────────────────────

    /** @deprecated Utiliser {@link Poste} et ses {@link Poste.Habilitation}. */
    @Deprecated
    public enum Role { DG, SECRETAIRE, SUBORDONNE, ADMIN_IT }

    /** Rôles secondaires cumulatifs, accordés en plus du poste principal. */
    public enum RoleSecondaire { SECRETAIRE }

    // ── Raccourcis métier ─────────────────────────────────────────────────────

    /**
     * Indique si cet utilisateur a accès au bureau d'envoi de documents.
     * Rétrocompatibilité : rôle legacy SECRETAIRE, rôle secondaire SECRETAIRE,
     * ou poste dont le code commence par "SEC" (SEC, SECRETAIRE…).
     */
    public boolean hasBureau() {
        if (Role.SECRETAIRE.equals(role)) return true;
        if (rolesSecondaires != null && rolesSecondaires.contains(RoleSecondaire.SECRETAIRE))
            return true;
        return poste != null && poste.getCode() != null
                && poste.getCode().toUpperCase().startsWith("SEC");
    }

    /**
     * Vérifie si cet utilisateur possède l'habilitation donnée via son poste.
     * Retourne false si aucun poste n'est affecté.
     */
    public boolean hasHabilitation(Poste.Habilitation habilitation) {
        return poste != null && poste.hasHabilitation(habilitation);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    // ── UserDetails (Spring Security) ────────────────────────────────────────
    // Les authorities réelles sont injectées par AuthFilter via le SecurityContext.
    // getAuthorities() retourne une liste vide ici car elles sont calculées dynamiquement.
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return Collections.emptyList(); }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return actif; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return actif; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    /** @deprecated Utiliser {@link #getPoste()} */
    @Deprecated
    public Role getRole() { return role; }

    /** @deprecated Utiliser {@link #setPoste(Poste)} */
    @Deprecated
    public void setRole(Role role) { this.role = role; }

    public Poste getPoste() { return poste; }
    public void setPoste(Poste poste) { this.poste = poste; }

    public AppUser getManager() { return manager; }
    public void setManager(AppUser manager) { this.manager = manager; }

    public List<AppUser> getSubordonnes() { return subordonnes; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public Set<RoleSecondaire> getRolesSecondaires() { return rolesSecondaires; }
    public void setRolesSecondaires(Set<RoleSecondaire> rolesSecondaires) { this.rolesSecondaires = rolesSecondaires; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
