package com.dgcockpit.service;

import com.dgcockpit.entity.WopiToken;
import com.dgcockpit.exception.AccesRefuseException;
import com.dgcockpit.repository.WopiTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Émission, validation et invalidation des tokens WOPI.
 *
 * <p>Le token est court (TTL configurable, 1h par défaut) et lié à un couple
 * (utilisateur, document) avec un droit d'écriture {@code canWrite} figé à
 * l'émission. La validation est appelée par {@code WopiController} pour chaque
 * requête entrante de Collabora.</p>
 */
@Service
public class WopiTokenService {

    private final WopiTokenRepository repo;

    @Value("${collabora.wopi.token-ttl:3600}")
    private long ttlSeconds;

    public WopiTokenService(WopiTokenRepository repo) {
        this.repo = repo;
    }

    /** Émet un token pour un (utilisateur, document, droit) donné. */
    public WopiToken issue(String userId, String bureauDocumentId, boolean canWrite) {
        WopiToken t = new WopiToken();
        t.setToken(UUID.randomUUID().toString().replace("-", ""));
        t.setUserId(userId);
        t.setBureauDocumentId(bureauDocumentId);
        t.setCanWrite(canWrite);
        t.setCreatedAt(Instant.now());
        t.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        return repo.save(t);
    }

    /**
     * Valide le token pour un document cible. Lance {@link AccesRefuseException}
     * (→ 403) si le token est inconnu, expiré, ou ne correspond pas au document.
     */
    public WopiToken validate(String rawToken, String bureauDocumentId) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AccesRefuseException("Token WOPI manquant");
        }
        WopiToken t = repo.findByToken(rawToken)
            .orElseThrow(() -> new AccesRefuseException("Token WOPI inconnu"));
        if (Instant.now().isAfter(t.getExpiresAt())) {
            throw new AccesRefuseException("Token WOPI expiré");
        }
        if (!t.getBureauDocumentId().equals(bureauDocumentId)) {
            throw new AccesRefuseException("Token/fichier incohérents");
        }
        return t;
    }

    /** Invalide tous les tokens d'un document (appelé après signature finale). */
    public void invalidateForDocument(String bureauDocumentId) {
        repo.deleteByBureauDocumentId(bureauDocumentId);
    }

    /** Purge nocturne des tokens expirés. */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpired() {
        repo.deleteByExpiresAtBefore(Instant.now());
    }
}
