package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "classeur_documents")
public class ClasseurDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classeur_id", nullable = false)
    private Classeur classeur;

    // Type du document source
    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    // ID du document source (CourrierDepart, CourrierArrive, PdfDocument…)
    private String documentId;

    // Champs dénormalisés pour l'affichage sans jointure
    private String titre;
    private String reference;
    private String destinataire;
    private LocalDate dateDocument;

    // Scan de preuve de livraison (bucket ged-livraison-scans)
    private String scanKey;

    private LocalDateTime classeAt = LocalDateTime.now();

    public enum DocumentType { COURRIER_DEPART, COURRIER_ARRIVE, PDF_DOCUMENT }

    public String getId() { return id; }
    public Classeur getClasseur() { return classeur; }
    public void setClasseur(Classeur classeur) { this.classeur = classeur; }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getDestinataire() { return destinataire; }
    public void setDestinataire(String destinataire) { this.destinataire = destinataire; }
    public LocalDate getDateDocument() { return dateDocument; }
    public void setDateDocument(LocalDate dateDocument) { this.dateDocument = dateDocument; }
    public String getScanKey() { return scanKey; }
    public void setScanKey(String scanKey) { this.scanKey = scanKey; }
    public LocalDateTime getClasseAt() { return classeAt; }
}
