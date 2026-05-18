package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "proof_types")
public class ProofType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String label;

    private String acceptedFormats;

    private String description;

    private boolean actif = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getAcceptedFormats() { return acceptedFormats; }
    public void setAcceptedFormats(String acceptedFormats) { this.acceptedFormats = acceptedFormats; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
