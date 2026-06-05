package com.dgcockpit.service;

import com.dgcockpit.entity.*;
import com.dgcockpit.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Génère un WorkflowDefinition par défaut à partir d'un TypeDocument existant.
 *
 * Structure générée :
 *   DOCUMENT_CREATION (ordre 0, autoTransition) →
 *   SIGNATURE         (ordre 1, configJson = circuitJson du TypeDocument) →
 *   END               (ordre 2)
 *
 * Le circuitJson d'origine est conservé intégralement (rétrocompatibilité).
 * Si un workflow est déjà associé au TypeDocument, la méthode retourne l'existant sans créer de doublon.
 */
@Service
public class WorkflowMigrationService {

    private final TypeDocumentRepository typeDocRepo;
    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowStepRepository stepRepo;
    private final ObjectMapper objectMapper;

    public WorkflowMigrationService(TypeDocumentRepository typeDocRepo,
                                     WorkflowDefinitionRepository definitionRepo,
                                     WorkflowStepRepository stepRepo,
                                     ObjectMapper objectMapper) {
        this.typeDocRepo    = typeDocRepo;
        this.definitionRepo = definitionRepo;
        this.stepRepo       = stepRepo;
        this.objectMapper   = objectMapper;
    }

    @Transactional
    public WorkflowDefinition generateDefaultWorkflow(String typeDocumentId) {
        TypeDocument td = typeDocRepo.findById(typeDocumentId)
            .orElseThrow(() -> new IllegalArgumentException("TypeDocument introuvable: " + typeDocumentId));

        // Si un workflow est déjà lié, retourner l'existant
        if (td.getWorkflowDefinitionId() != null) {
            return definitionRepo.findById(td.getWorkflowDefinitionId())
                .orElseThrow(() -> new IllegalStateException("WorkflowDefinition liée introuvable"));
        }

        // Créer la définition
        WorkflowDefinition def = new WorkflowDefinition();
        def.setCode(sanitizeCode(td.getCode()) + "_WF");
        def.setLibelle("Circuit " + td.getLibelle());
        def.setDescription("Généré automatiquement depuis le type de document « " + td.getLibelle() + " ».");
        def.setActif(false); // inactif par défaut — l'admin active après vérification
        def.setVersion(1);
        WorkflowDefinition saved = definitionRepo.save(def);

        // Étape 1 : DOCUMENT_CREATION
        WorkflowStep creation = new WorkflowStep();
        creation.setWorkflowId(saved.getId());
        creation.setOrdre(0);
        creation.setCode("DOC_CREATION");
        creation.setLibelle("Création du document");
        creation.setStepType(StepType.DOCUMENT_CREATION);
        creation.setAutoTransition(true);
        WorkflowStep savedCreation = stepRepo.save(creation);

        // Étape 3 : END (créée avant SIGNATURE pour pouvoir référencer son id)
        WorkflowStep end = new WorkflowStep();
        end.setWorkflowId(saved.getId());
        end.setOrdre(2);
        end.setCode("END");
        end.setLibelle("Fin du processus");
        end.setStepType(StepType.END);
        WorkflowStep savedEnd = stepRepo.save(end);

        // Étape 2 : SIGNATURE — encapsule le circuitJson existant dans configJson
        WorkflowStep signature = new WorkflowStep();
        signature.setWorkflowId(saved.getId());
        signature.setOrdre(1);
        signature.setCode("SIGNATURE");
        signature.setLibelle("Circuit de signature");
        signature.setStepType(StepType.SIGNATURE);
        signature.setNextStepId(savedEnd.getId());

        // Stocker le circuitJson d'origine dans configJson pour que handleSignature() puisse l'utiliser
        if (td.getCircuitJson() != null && !td.getCircuitJson().isBlank()) {
            try {
                String configJson = objectMapper.writeValueAsString(
                    Map.of("circuitJson", td.getCircuitJson()));
                signature.setConfigJson(configJson);
            } catch (Exception ignored) {}
        }
        stepRepo.save(signature);

        // Relier DOCUMENT_CREATION → SIGNATURE
        savedCreation.setNextStepId(signature.getId());
        stepRepo.save(savedCreation);

        // Lier le TypeDocument au nouveau workflow
        td.setWorkflowDefinitionId(saved.getId());
        typeDocRepo.save(td);

        return saved;
    }

    private String sanitizeCode(String code) {
        if (code == null) return "TYPE";
        return code.replaceAll("[^A-Z0-9_]", "_").toUpperCase();
    }
}
