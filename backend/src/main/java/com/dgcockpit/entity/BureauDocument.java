package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bureau_documents")
public class BureauDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String secretaireId;
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

    // Zone signature (coordonnées en % de la page)
    private Integer signatureZonePage;
    private Double signatureZoneX;
    private Double signatureZoneY;
    private Double signatureZoneW;
    private Double signatureZoneH;

    // Zone tampon (coordonnées en % de la page)
    private Integer stampZonePage;
    private Double stampZoneX;
    private Double stampZoneY;
    private Double stampZoneW;
    private Double stampZoneH;

    // Rempli après soumission au parapheur
    private String pdfDocumentId;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum Statut { BROUILLON, SOUMIS }

    public String getId() { return id; }
    public String getSecretaireId() { return secretaireId; }
    public void setSecretaireId(String secretaireId) { this.secretaireId = secretaireId; }
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
    public String getPdfDocumentId() { return pdfDocumentId; }
    public void setPdfDocumentId(String pdfDocumentId) { this.pdfDocumentId = pdfDocumentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
