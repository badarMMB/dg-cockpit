package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Définit la configuration d'un type de document circulant dans le bureau.
 * Remplace la liste codée en dur (COURRIER, NOTE_SERVICE, …).
 */
@Entity
@Table(name = "type_documents")
public class TypeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Code technique unique (ex. COURRIER, NOTE_SERVICE) */
    @Column(nullable = false, unique = true)
    private String code;

    /** Libellé affiché dans l'interface */
    @Column(nullable = false)
    private String libelle;

    /** Mode de constitution du circuit de signature */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModeCircuit modeCircuit = ModeCircuit.LIBRE;

    /** Traitement appliqué au document après la dernière signature */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionFinale actionFinale = ActionFinale.ARCHIVER;

    /**
     * Circuit pré-défini au format JSON : [{posteId, posteLibelle}, …] dans l'ordre d'approbation.
     * Utilisé uniquement si modeCircuit = PREDEFINI ou PREDEFINI_MODIFIABLE.
     */
    @Column(columnDefinition = "TEXT")
    private String circuitJson;

    /**
     * Postes habilités à initier ce type, au format JSON : [posteId, …].
     * Si null ou vide : tout utilisateur peut créer ce type.
     */
    @Column(columnDefinition = "TEXT")
    private String initiateurPostesJson;

    /** Zone de signature obligatoire avant soumission */
    private boolean requiresSignatureZone = true;

    /** Tampon obligatoire avant soumission */
    private boolean requiresStampZone = false;

    /** Champ destinataire obligatoire à la création */
    private boolean requiresDestinataire = false;

    /** Gabarit HTML pré-rempli au moment de la création d'un document de ce type.
     *  Pas de @Lob : sur Postgres + Hibernate 6, @Lob String tente un OID streaming
     *  ("Unable to access lob stream" hors transaction). TEXT supporte 1 GB, c'est suffisant. */
    @Column(columnDefinition = "TEXT")
    private String templateHtml;

    /** Chemin MinIO du .docx source (null si jamais importé). */
    @Column
    private String templateDocxPath;

    /** Chemin MinIO du PDF pré-converti (null si pas encore généré). */
    @Column
    private String templatePdfPath;

    private String workflowDefinitionId;

    private boolean actif = true;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum ModeCircuit {
        /** Envoyé automatiquement au manager direct — aucun picker affiché */
        MANAGER_SEUL,
        /** Picker complet à la soumission (comportement classique) */
        LIBRE,
        /** Circuit fixé par le type — résolu automatiquement au moment de la soumission */
        PREDEFINI,
        /** Circuit pré-rempli depuis le type, mais l'utilisateur peut le modifier */
        PREDEFINI_MODIFIABLE
    }

    public enum ActionFinale {
        /** Le document reste dans le parapheur avec statut SIGNÉ */
        ARCHIVER,
        /** Le document est publié (visible dans la liste des notes de service, etc.) */
        PUBLIER
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public ModeCircuit getModeCircuit() { return modeCircuit; }
    public void setModeCircuit(ModeCircuit modeCircuit) { this.modeCircuit = modeCircuit; }

    public ActionFinale getActionFinale() { return actionFinale; }
    public void setActionFinale(ActionFinale actionFinale) { this.actionFinale = actionFinale; }

    public String getCircuitJson() { return circuitJson; }
    public void setCircuitJson(String circuitJson) { this.circuitJson = circuitJson; }

    public String getInitiateurPostesJson() { return initiateurPostesJson; }
    public void setInitiateurPostesJson(String initiateurPostesJson) { this.initiateurPostesJson = initiateurPostesJson; }

    public boolean isRequiresSignatureZone() { return requiresSignatureZone; }
    public void setRequiresSignatureZone(boolean requiresSignatureZone) { this.requiresSignatureZone = requiresSignatureZone; }

    public boolean isRequiresStampZone() { return requiresStampZone; }
    public void setRequiresStampZone(boolean requiresStampZone) { this.requiresStampZone = requiresStampZone; }

    public boolean isRequiresDestinataire() { return requiresDestinataire; }
    public void setRequiresDestinataire(boolean requiresDestinataire) { this.requiresDestinataire = requiresDestinataire; }

    public String getTemplateHtml() { return templateHtml; }
    public void setTemplateHtml(String templateHtml) { this.templateHtml = templateHtml; }

    public String getTemplateDocxPath() { return templateDocxPath; }
    public void setTemplateDocxPath(String templateDocxPath) { this.templateDocxPath = templateDocxPath; }

    public String getTemplatePdfPath() { return templatePdfPath; }
    public void setTemplatePdfPath(String templatePdfPath) { this.templatePdfPath = templatePdfPath; }

    public String getWorkflowDefinitionId() { return workflowDefinitionId; }
    public void setWorkflowDefinitionId(String workflowDefinitionId) { this.workflowDefinitionId = workflowDefinitionId; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
