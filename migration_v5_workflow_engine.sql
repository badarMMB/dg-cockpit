-- ============================================================================
-- Migration v5 — Moteur de Workflow Générique
-- Date : 2026-06-05
--
-- Ajoute les colonnes workflowInstanceId et workflowStepId aux entités clés
-- pour permettre le pilotage des documents/instructions/PDF par un WorkflowInstance.
--
-- Les entités WorkflowDefinition, WorkflowStep, WorkflowInstance, WorkflowParticipant,
-- et WorkflowAction sont créées automatiquement par Hibernate (ddl-auto=update).
-- ============================================================================

-- ── TypeDocument: Lien vers WorkflowDefinition ────────────────────────────────
-- TypeDocument.workflowDefinitionId est créé automatiquement par Hibernate

-- ── BureauDocument: Workflow links ──────────────────────────────────────────────
-- Les champs workflowInstanceId et workflowStepId sont créés automatiquement
-- par Hibernate pour supporter les workflows génériques

-- ── Instruction: Workflow links ─────────────────────────────────────────────────
-- Les champs workflowInstanceId et workflowStepId sont créés automatiquement
-- par Hibernate pour supporter les workflows génériques

-- ── PdfDocument: Workflow links ─────────────────────────────────────────────────
-- Les champs workflowInstanceId et workflowStepId sont créés automatiquement
-- par Hibernate pour supporter les workflows génériques

-- ── Workflow Step Configuration ─────────────────────────────────────────────────
-- WorkflowStep.configJson est créé automatiquement et stockera:
-- - Pour SIGNATURE steps: instructionTypeId, typeDocumentId, circuitJson, signatureOptions
-- - Pour INSTRUCTION steps: instructionTypeId, typeDocumentId, participants
-- - Pour autres steps: paramètres spécifiques au type d'étape

-- ── Indexes pour performance ────────────────────────────────────────────────────
-- Création des indexes pour les recherches de workflows
CREATE INDEX IF NOT EXISTS idx_bureau_documents_workflow_instance 
    ON bureau_documents(workflow_instance_id);
CREATE INDEX IF NOT EXISTS idx_bureau_documents_workflow_step 
    ON bureau_documents(workflow_step_id);

CREATE INDEX IF NOT EXISTS idx_instruction_workflow_instance 
    ON instructions(workflow_instance_id);
CREATE INDEX IF NOT EXISTS idx_instruction_workflow_step 
    ON instructions(workflow_step_id);

CREATE INDEX IF NOT EXISTS idx_pdf_documents_workflow_instance 
    ON pdf_documents(workflow_instance_id);
CREATE INDEX IF NOT EXISTS idx_pdf_documents_workflow_step 
    ON pdf_documents(workflow_step_id);

-- ── Historique des transitions de workflow ──────────────────────────────────────
-- WorkflowAction sera créée automatiquement avec champs:
-- - id, workflowInstanceId, workflowStepId, actionType, actorId, comment, createdAt
-- - actionType énumération: STARTED, STEP_COMPLETED, STEP_SKIPPED, REJECTED, APPROVED, etc.

-- ── Participants du workflow ────────────────────────────────────────────────────
-- WorkflowParticipant sera créée automatiquement avec champs:
-- - id, workflowStepId, posteId, roleParticipant, resolvedUserIds (JSON array)

-- ============================================================================
-- Remarques importantes:
-- - Pas d'introduction de Flyway/Liquibase ; on continue avec scripts SQL versionnés
-- - Hibernat e ddl-auto=update crée automatiquement les tables et colonnes
-- - Ce script sert de source de vérité pour les indexes et configuration manuelle
-- ============================================================================
