package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bureau_documents")
public class BureauDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String proprietaireId;
    private String titre;

    // COURRIER | NOTE_SERVICE | DECISION | TRANSMISSION | INVITATION | VOEUX | AUTRE
    private String type;

    private String destinataire;
    private String originalFileName;
    private String bucket;
    private String objectKey;
    private Integer pageCount;

    @Enumerated(EnumType.STRING)
    private Statut statut = Statut.BROUILLON;

    // Zone signature (coordonnées en % de la page) — legacy, conservé pour compat
    private Integer signatureZonePage;
    private Double signatureZoneX;
    private Double signatureZoneY;
    private Double signatureZoneW;
    private Double signatureZoneH;
    @Column(columnDefinition = "boolean DEFAULT false")
    private boolean signatureZoneAllPages = false;

    // Zone tampon — legacy
    private Integer stampZonePage;
    private Double stampZoneX;
    private Double stampZoneY;
    private Double stampZoneW;
    private Double stampZoneH;
    @Column(columnDefinition = "boolean DEFAULT false")
    private boolean stampZoneAllPages = false;

    // Zones multi-pages (JSON) — [{page,x,y,w,h}, ...]
    @Column(columnDefinition = "TEXT")
    private String signatureZonesJson;

    @Column(columnDefinition = "TEXT")
    private String stampZonesJson;

    // PDF régénéré à chaque enregistrement Collabora — support du placement de zones + signature PDFBox
    private String signaturePdfKey;

    // Rempli après soumission au parapheur
    private String pdfDocumentId;

    /**
     * ID de l'instruction liée à ce document (instruction DOCUMENTAIRE).
     * Permet de reporter les événements du circuit dans le fil de l'instruction.
     * Remplace correctionInstructionId (ancien modèle).
     */
    private String sourceInstructionId;

    // Motif de renvoi par le DG
    @Column(columnDefinition = "TEXT")
    private String renvoyeMotif;

    // Fichier corrigé uploadé après renvoi — débloque le bouton Re-soumettre
    @Column(columnDefinition = "boolean DEFAULT false")
    private boolean corrigeDepuisRenvoi = false;

    // Surlignages posés par le DG avant le renvoi — JSON [{page,x,y,w,h}, ...]
    @Column(columnDefinition = "TEXT")
    private String highlightsJson;

    // Clé MinIO du scan de preuve de livraison
    private String scanLivraisonKey;

    // Référence administrative générée à la soumission : ex. N°045/DG/2026
    private String reference;
    private Integer referenceNumber;
    private Integer referenceYear;

    // Circuit multi-signataires — rempli si ce document est un document de transit
    // (créé dans le bureau d'un signataire intermédiaire après qu'il a signé)
    private String circuitPdfDocumentId;  // ID du PdfDocument parent (circuit en cours)
    private int circuitNextStep = 0;       // Étape suivante à activer dans ce circuit

    // Référence au TypeDocument configuré dans les Paramètres (nullable pour compat ascendante)
    private String typeDocumentId;

    // Timestamps de chaque changement de statut (pour la timeline)
    private LocalDateTime soumisAt;
    private LocalDateTime signeAt;
    private LocalDateTime retourneAt;
    private LocalDateTime livreAt;

    private final LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum Statut { BROUILLON, SOUMIS, RETOURNE, SIGNE, LIVRE }

    public String getId() { return id; }
    public String getProprietaireId() { return proprietaireId; }
    public void setProprietaireId(String proprietaireId) { this.proprietaireId = proprietaireId; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDestinataire() { return destinataire; }
    public void setDestinataire(String destinataire) { this.destinataire = destinataire; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public Integer getSignatureZonePage() { return signatureZonePage; }
    public void setSignatureZonePage(Integer signatureZonePage) { this.signatureZonePage = signatureZonePage; }
    public Double getSignatureZoneX() { return signatureZoneX; }
    public void setSignatureZoneX(Double signatureZoneX) { this.signatureZoneX = signatureZoneX; }
    public Double getSignatureZoneY() { return signatureZoneY; }
    public void setSignatureZoneY(Double signatureZoneY) { this.signatureZoneY = signatureZoneY; }
    public Double getSignatureZoneW() { return signatureZoneW; }
    public void setSignatureZoneW(Double signatureZoneW) { this.signatureZoneW = signatureZoneW; }
    public Double getSignatureZoneH() { return signatureZoneH; }
    public void setSignatureZoneH(Double signatureZoneH) { this.signatureZoneH = signatureZoneH; }
    public boolean isSignatureZoneAllPages() { return signatureZoneAllPages; }
    public void setSignatureZoneAllPages(boolean v) { this.signatureZoneAllPages = v; }
    public Integer getStampZonePage() { return stampZonePage; }
    public void setStampZonePage(Integer stampZonePage) { this.stampZonePage = stampZonePage; }
    public Double getStampZoneX() { return stampZoneX; }
    public void setStampZoneX(Double stampZoneX) { this.stampZoneX = stampZoneX; }
    public Double getStampZoneY() { return stampZoneY; }
    public void setStampZoneY(Double stampZoneY) { this.stampZoneY = stampZoneY; }
    public Double getStampZoneW() { return stampZoneW; }
    public void setStampZoneW(Double stampZoneW) { this.stampZoneW = stampZoneW; }
    public Double getStampZoneH() { return stampZoneH; }
    public void setStampZoneH(Double stampZoneH) { this.stampZoneH = stampZoneH; }
    public boolean isStampZoneAllPages() { return stampZoneAllPages; }
    public void setStampZoneAllPages(boolean v) { this.stampZoneAllPages = v; }
    public String getRenvoyeMotif() { return renvoyeMotif; }
    public void setRenvoyeMotif(String renvoyeMotif) { this.renvoyeMotif = renvoyeMotif; }
    public boolean isCorrigeDepuisRenvoi() { return corrigeDepuisRenvoi; }
    public void setCorrigeDepuisRenvoi(boolean v) { this.corrigeDepuisRenvoi = v; }
    public String getHighlightsJson() { return highlightsJson; }
    public void setHighlightsJson(String highlightsJson) { this.highlightsJson = highlightsJson; }
    public String getSignatureZonesJson() { return signatureZonesJson; }
    public void setSignatureZonesJson(String signatureZonesJson) { this.signatureZonesJson = signatureZonesJson; }
    public String getStampZonesJson() { return stampZonesJson; }
    public void setStampZonesJson(String stampZonesJson) { this.stampZonesJson = stampZonesJson; }
    public String getSignaturePdfKey() { return signaturePdfKey; }
    public void setSignaturePdfKey(String signaturePdfKey) { this.signaturePdfKey = signaturePdfKey; }
    public String getPdfDocumentId() { return pdfDocumentId; }
    public void setPdfDocumentId(String pdfDocumentId) { this.pdfDocumentId = pdfDocumentId; }
    public String getSourceInstructionId() { return sourceInstructionId; }
    public void setSourceInstructionId(String v) { this.sourceInstructionId = v; }
    public String getScanLivraisonKey() { return scanLivraisonKey; }
    public void setScanLivraisonKey(String scanLivraisonKey) { this.scanLivraisonKey = scanLivraisonKey; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Integer getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(Integer referenceNumber) { this.referenceNumber = referenceNumber; }
    public Integer getReferenceYear() { return referenceYear; }
    public void setReferenceYear(Integer referenceYear) { this.referenceYear = referenceYear; }
    public LocalDateTime getSoumisAt() { return soumisAt; }
    public void setSoumisAt(LocalDateTime soumisAt) { this.soumisAt = soumisAt; }
    public LocalDateTime getSigneAt() { return signeAt; }
    public void setSigneAt(LocalDateTime signeAt) { this.signeAt = signeAt; }
    public LocalDateTime getRetourneAt() { return retourneAt; }
    public void setRetourneAt(LocalDateTime retourneAt) { this.retourneAt = retourneAt; }
    public LocalDateTime getLivreAt() { return livreAt; }
    public void setLivreAt(LocalDateTime livreAt) { this.livreAt = livreAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getCircuitPdfDocumentId() { return circuitPdfDocumentId; }
    public void setCircuitPdfDocumentId(String circuitPdfDocumentId) { this.circuitPdfDocumentId = circuitPdfDocumentId; }
    public int getCircuitNextStep() { return circuitNextStep; }
    public void setCircuitNextStep(int circuitNextStep) { this.circuitNextStep = circuitNextStep; }
    public String getTypeDocumentId() { return typeDocumentId; }
    public void setTypeDocumentId(String typeDocumentId) { this.typeDocumentId = typeDocumentId; }
}
