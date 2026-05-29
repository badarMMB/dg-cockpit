package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "circuit_signatures")
public class CircuitSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // FK logique vers pdf_documents.id
    private String pdfDocumentId;

    // 0-based — ordre dans la chaîne de signature
    private int stepOrder;

    private String signaireUserId;
    private String signaireNom;

    // Zones pré-définies pour ce signataire [{page,x,y,w,h}, ...]
    @Column(columnDefinition = "TEXT")
    private String signatureZonesJson;

    @Enumerated(EnumType.STRING)
    private StatutEtape statut = StatutEtape.EN_ATTENTE;

    private LocalDateTime signedAt;

    public enum StatutEtape { EN_ATTENTE, SIGNE, RENVOYE, ANNULE }

    public String getId() { return id; }

    public String getPdfDocumentId() { return pdfDocumentId; }
    public void setPdfDocumentId(String pdfDocumentId) { this.pdfDocumentId = pdfDocumentId; }

    public int getStepOrder() { return stepOrder; }
    public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }

    public String getSignaireUserId() { return signaireUserId; }
    public void setSignaireUserId(String signaireUserId) { this.signaireUserId = signaireUserId; }

    public String getSignaireNom() { return signaireNom; }
    public void setSignaireNom(String signaireNom) { this.signaireNom = signaireNom; }

    public String getSignatureZonesJson() { return signatureZonesJson; }
    public void setSignatureZonesJson(String signatureZonesJson) { this.signatureZonesJson = signatureZonesJson; }

    public StatutEtape getStatut() { return statut; }
    public void setStatut(StatutEtape statut) { this.statut = statut; }

    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
}
