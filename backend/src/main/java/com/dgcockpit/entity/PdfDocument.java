package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pdf_documents")
public class PdfDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String title;
    private String originalFileName;

    private String bucket;
    private String objectKey;

    private int pageCount;

    private String status; // DRAFT | FINALIZED

    private String finalizedObjectKey;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── Parapheur ────────────────────────────────────────────────────────────

    public enum ParapheurStatut {
        EN_ATTENTE_SIGNATURE, SIGNE, REFUSE, RENVOYE, ARCHIVE, PUBLIE
    }

    public enum ParapheurType {
        COURRIER, NOTE_SERVICE
    }

    @Enumerated(EnumType.STRING)
    private ParapheurStatut parapheurStatut;

    @Enumerated(EnumType.STRING)
    private ParapheurType parapheurType;

    private String submittedBy;
    private LocalDateTime submittedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionComment;

    private LocalDateTime signedAt;

    private String destinataire;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFinalizedObjectKey() { return finalizedObjectKey; }
    public void setFinalizedObjectKey(String finalizedObjectKey) { this.finalizedObjectKey = finalizedObjectKey; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public ParapheurStatut getParapheurStatut() { return parapheurStatut; }
    public void setParapheurStatut(ParapheurStatut parapheurStatut) { this.parapheurStatut = parapheurStatut; }

    public ParapheurType getParapheurType() { return parapheurType; }
    public void setParapheurType(ParapheurType parapheurType) { this.parapheurType = parapheurType; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getRejectionComment() { return rejectionComment; }
    public void setRejectionComment(String rejectionComment) { this.rejectionComment = rejectionComment; }

    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }

    public String getDestinataire() { return destinataire; }
    public void setDestinataire(String destinataire) { this.destinataire = destinataire; }
}
