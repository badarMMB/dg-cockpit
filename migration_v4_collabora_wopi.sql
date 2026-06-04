-- ============================================================================
-- Migration v4 — Intégration Collabora Online (WOPI)
-- Date : 2026-06-03
-- Lot 1 — Infrastructure WOPI socle
--
-- Note : avec ddl-auto=update, Hibernate crée la table wopi_tokens automatiquement.
-- Ce script reste la source de vérité du schéma et permet une application manuelle
-- (environnements où ddl-auto est désactivé).
-- ============================================================================

-- Table des tokens WOPI à durée de vie limitée (session d'édition Collabora)
CREATE TABLE IF NOT EXISTS wopi_tokens (
    token               VARCHAR(64)  PRIMARY KEY,
    user_id             VARCHAR(64)  NOT NULL,
    bureau_document_id  VARCHAR(64)  NOT NULL,
    can_write           BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at          TIMESTAMP    NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wopi_expires ON wopi_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_wopi_doc     ON wopi_tokens(bureau_document_id);
