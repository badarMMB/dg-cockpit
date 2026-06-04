-- Migration v3 — Simplification du module Instructions
-- À exécuter sur la base après déploiement du code

-- InstructionType : nature de l'instruction + type de document livrable attendu
ALTER TABLE instruction_types ADD COLUMN IF NOT EXISTS type_instruction VARCHAR(20) DEFAULT 'LIBRE';
ALTER TABLE instruction_types ADD COLUMN IF NOT EXISTS type_document_attendu_id VARCHAR(255);

-- BureauDocument : lien vers l'instruction source (remplace correction_instruction_id)
ALTER TABLE bureau_documents ADD COLUMN IF NOT EXISTS source_instruction_id VARCHAR(255);

-- TypeDocument : lien vers le type d'instruction lié (lien bidirectionnel avec instruction_types)
ALTER TABLE type_documents ADD COLUMN IF NOT EXISTS linked_instruction_type_id VARCHAR(255);

-- InstructionMessage : flag message système (remplace type_message enum)
ALTER TABLE instruction_messages ADD COLUMN IF NOT EXISTS is_system_message BOOLEAN DEFAULT FALSE;

-- Rétro-compat : marquer les messages existants de type SYSTEM comme messages système
UPDATE instruction_messages SET is_system_message = TRUE WHERE type_message = 'SYSTEM';

-- Optionnel : migrer les statuts legacy vers les 3 statuts simplifiés si nécessaire
-- (à adapter selon le nom exact de la colonne statut sur votre installation)
-- UPDATE instructions SET statut = 'CLOTURE' WHERE statut IN ('CLOTURE', 'REFUSE');
-- UPDATE instructions SET statut = 'EN_COURS' WHERE statut IN ('EN_ATTENTE', 'SOUMIS_VALIDATION');
-- UPDATE instructions SET statut = 'OUVERT' WHERE statut NOT IN ('EN_COURS', 'CLOTURE');
