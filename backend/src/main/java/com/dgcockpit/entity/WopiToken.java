package com.dgcockpit.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Token WOPI à durée de vie limitée, émis pour une session d'édition Collabora.
 *
 * <p>Distinct du JWT applicatif : Collabora envoie ce token en query param
 * ({@code ?access_token=...}) lors de ses appels server-to-server aux endpoints
 * {@code /api/wopi/**}. La sécurité de ces endpoints repose entièrement sur la
 * validation de ce token (cf. WopiTokenService), car {@code /api/wopi/**} est
 * exempté du filtre JWT.</p>
 */
@Entity
@Table(name = "wopi_tokens", indexes = {
    @Index(name = "idx_wopi_expires", columnList = "expiresAt"),
    @Index(name = "idx_wopi_doc", columnList = "bureauDocumentId")
})
public class WopiToken {

    @Id
    private String token;               // UUID v4 sans tirets

    @Column(nullable = false)
    private String userId;              // AppUser.id propriétaire du token

    @Column(nullable = false)
    private String bureauDocumentId;    // cible immutable

    @Column(nullable = false)
    private boolean canWrite;           // droit gravé à l'émission, non modifiable

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getBureauDocumentId() { return bureauDocumentId; }
    public void setBureauDocumentId(String bureauDocumentId) { this.bureauDocumentId = bureauDocumentId; }
    public boolean isCanWrite() { return canWrite; }
    public void setCanWrite(boolean canWrite) { this.canWrite = canWrite; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
