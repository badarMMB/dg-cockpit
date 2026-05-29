-- ─────────────────────────────────────────────────────────────────────────
-- Migration v2 — Circuit de signature multi-signataires
-- À exécuter UNE SEULE FOIS avant ou après le déploiement de la v2
-- (Hibernate ddl-auto=update crée automatiquement les nouvelles tables/colonnes
--  mais ne gère pas le renommage de colonnes existantes)
-- ─────────────────────────────────────────────────────────────────────────

-- ── 1. Renommer secretaire_id → proprietaire_id dans bureau_documents ────
-- ATTENTION : si Hibernate a déjà créé la colonne proprietaire_id vide,
-- supprimer d'abord la colonne vide avant de renommer.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'bureau_documents' AND column_name = 'proprietaire_id'
    ) THEN
        -- Hibernate a créé proprietaire_id vide : copier les données puis supprimer l'ancienne
        UPDATE bureau_documents SET proprietaire_id = secretaire_id WHERE proprietaire_id IS NULL;
        ALTER TABLE bureau_documents DROP COLUMN IF EXISTS secretaire_id;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'bureau_documents' AND column_name = 'secretaire_id'
    ) THEN
        -- Renommage direct si Hibernate n'a pas encore ajouté proprietaire_id
        ALTER TABLE bureau_documents RENAME COLUMN secretaire_id TO proprietaire_id;
    END IF;
END
$$;

-- ── 2. Nouvelles colonnes dans pdf_documents (si ddl-auto=update ne l'a pas fait) ──
ALTER TABLE pdf_documents
    ADD COLUMN IF NOT EXISTS current_signataire_user_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS current_circuit_step INT DEFAULT 0;

-- ── 3. Nouvelle table circuit_signatures (si ddl-auto=update ne l'a pas fait) ──
CREATE TABLE IF NOT EXISTS circuit_signatures (
    id VARCHAR(255) PRIMARY KEY,
    pdf_document_id VARCHAR(255) NOT NULL,
    step_order INT NOT NULL,
    signataire_user_id VARCHAR(255),
    signataire_nom VARCHAR(255),
    signature_zones_json TEXT,
    statut VARCHAR(50) DEFAULT 'EN_ATTENTE',
    signed_at TIMESTAMP
);

-- ── 4. Nouvelle table app_user_secondary_roles (si ddl-auto=update ne l'a pas fait) ──
CREATE TABLE IF NOT EXISTS app_user_secondary_roles (
    user_id VARCHAR(255) NOT NULL,
    role_secondaire VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role_secondaire)
);

-- ── 5. Rétro-compatibilité : documents EN_ATTENTE_SIGNATURE existants ────
-- Pointer vers le DG pour les documents qui n'ont pas encore de signataire assigné
UPDATE pdf_documents
SET current_signataire_user_id = (
    SELECT id FROM app_users WHERE role = 'DG' LIMIT 1
)
WHERE parapheur_statut = 'EN_ATTENTE_SIGNATURE'
  AND current_signataire_user_id IS NULL;

-- ── 6. Nouvelles colonnes bureau_documents — transit multi-signataires ───
ALTER TABLE bureau_documents
    ADD COLUMN IF NOT EXISTS circuit_pdf_document_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS circuit_next_step INT DEFAULT 0;

-- ── 7. Types de documents configurables ─────────────────────────────────
-- Hibernate ddl-auto=update crée la table type_documents automatiquement.
-- Ce bloc insère les types legacy avec des valeurs par défaut sensées
-- (ne rien insérer si la table est déjà peuplée).

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'type_documents')
       AND NOT EXISTS (SELECT 1 FROM type_documents LIMIT 1) THEN

        INSERT INTO type_documents
            (id, code, libelle, mode_circuit, action_finale,
             requires_signature_zone, requires_stamp_zone, requires_destinataire,
             actif, created_at, updated_at)
        VALUES
            (gen_random_uuid(), 'COURRIER',     'Courrier officiel',       'LIBRE', 'ARCHIVER', true,  false, true,  true, now(), now()),
            (gen_random_uuid(), 'NOTE_SERVICE', 'Note de service',         'LIBRE', 'PUBLIER',  true,  false, false, true, now(), now()),
            (gen_random_uuid(), 'DECISION',     'Décision',                'LIBRE', 'ARCHIVER', true,  false, false, true, now(), now()),
            (gen_random_uuid(), 'TRANSMISSION', 'Lettre de transmission',  'LIBRE', 'ARCHIVER', true,  false, true,  true, now(), now()),
            (gen_random_uuid(), 'INVITATION',   'Invitation',              'LIBRE', 'ARCHIVER', true,  false, false, true, now(), now()),
            (gen_random_uuid(), 'VOEUX',        'Vœux',                    'LIBRE', 'ARCHIVER', true,  false, false, true, now(), now()),
            (gen_random_uuid(), 'AUTRE',        'Autre',                   'LIBRE', 'ARCHIVER', true,  false, false, true, now(), now());

    END IF;
END
$$;

-- ── 8. Colonne type_document_id dans bureau_documents ────────────────────
ALTER TABLE bureau_documents
    ADD COLUMN IF NOT EXISTS type_document_id VARCHAR(255);

-- ── Fin ──────────────────────────────────────────────────────────────────
