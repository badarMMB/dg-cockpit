package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "page_annotations")
public class PageAnnotation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // format: "{documentId}::{pageNumber}" (0-indexed)
    private String pageId;

    private String annotationType; // SIGNATURE | STAMP | TEXT_NOTE | RECTANGLE | HIGHLIGHT | ARROW

    private String signatureAssetId; // nullable — set when annotationType is SIGNATURE or STAMP

    @Column(columnDefinition = "TEXT")
    private String textContent; // nullable — set when annotationType is TEXT_NOTE

    private double xPercent;
    private double yPercent;
    private double widthPercent;
    private double heightPercent;

    private String createdBy;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getId() { return id; }

    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }

    public String getAnnotationType() { return annotationType; }
    public void setAnnotationType(String annotationType) { this.annotationType = annotationType; }

    public String getSignatureAssetId() { return signatureAssetId; }
    public void setSignatureAssetId(String signatureAssetId) { this.signatureAssetId = signatureAssetId; }

    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }

    public double getXPercent() { return xPercent; }
    public void setXPercent(double xPercent) { this.xPercent = xPercent; }

    public double getYPercent() { return yPercent; }
    public void setYPercent(double yPercent) { this.yPercent = yPercent; }

    public double getWidthPercent() { return widthPercent; }
    public void setWidthPercent(double widthPercent) { this.widthPercent = widthPercent; }

    public double getHeightPercent() { return heightPercent; }
    public void setHeightPercent(double heightPercent) { this.heightPercent = heightPercent; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
