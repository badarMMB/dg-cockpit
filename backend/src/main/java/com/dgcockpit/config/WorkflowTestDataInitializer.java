package com.dgcockpit.config;

import com.dgcockpit.entity.*;
import com.dgcockpit.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Initialise les données de test pour le workflow "Ordre de Mission".
 *
 * Crée (si absent) :
 *   - 3 Postes : DIRECTEUR_GENERAL, SECRETARIAT_DG, CHEF_SERVICE
 *   - Affectation des utilisateurs existants à leurs postes
 *   - TypeDocument ORDRE_MISSION
 *   - WorkflowDefinition ORDRE_MISSION_WF avec 4 étapes
 *   - WorkflowParticipant pour chaque étape métier
 */
@Configuration
public class WorkflowTestDataInitializer {

    @Bean
    CommandLineRunner seedWorkflowOdreMission(
            PosteRepository posteRepo,
            AppUserRepository userRepo,
            TypeDocumentRepository typeDocRepo,
            WorkflowDefinitionRepository wfDefRepo,
            WorkflowStepRepository wfStepRepo,
            WorkflowParticipantRepository wfPartRepo) {

        return args -> {
            // Ne ré-exécuter que si le workflow n'existe pas encore
            if (wfDefRepo.findAll().stream()
                    .anyMatch(w -> "ORDRE_MISSION_WF".equals(w.getCode()))) {
                // Déjà initialisé — s'assurer quand même que les postes sont affectés
                assignPostesToUsers(posteRepo, userRepo);
                return;
            }

            // ── 1. Postes ──────────────────────────────────────────────────
            Poste posteDg  = upsertPoste(posteRepo, "DIRECTEUR_GENERAL", "Directeur Général",
                    Set.of(Poste.Habilitation.CAN_SIGN,
                           Poste.Habilitation.CAN_MANAGE_TYPES,
                           Poste.Habilitation.CAN_MANAGE_USERS,
                           Poste.Habilitation.CAN_VIEW_ALL,
                           Poste.Habilitation.CAN_VALIDATE,
                           Poste.Habilitation.CAN_CLOSE));

            Poste posteSec = upsertPoste(posteRepo, "SECRETARIAT_DG", "Secrétariat de Direction",
                    Set.of(Poste.Habilitation.CAN_CREATE_INSTRUCTION));

            Poste posteChef = upsertPoste(posteRepo, "CHEF_SERVICE", "Chef de Service",
                    Set.of(Poste.Habilitation.CAN_CREATE_INSTRUCTION,
                           Poste.Habilitation.CAN_VALIDATE));

            // ── 2. Affectation des utilisateurs ───────────────────────────
            setPoste(userRepo, "dg",           posteDg);
            setPoste(userRepo, "secretaire",   posteSec);
            setPoste(userRepo, "agent.douane", posteChef);

            // ── 3. TypeDocument ORDRE_MISSION ─────────────────────────────
            TypeDocument td = typeDocRepo.findAll().stream()
                    .filter(t -> "ORDRE_MISSION".equals(t.getCode()))
                    .findFirst().orElseGet(() -> {
                        TypeDocument n = new TypeDocument();
                        n.setCode("ORDRE_MISSION");
                        n.setLibelle("Ordre de Mission");
                        n.setModeCircuit(TypeDocument.ModeCircuit.PREDEFINI);
                        n.setActionFinale(TypeDocument.ActionFinale.ARCHIVER);
                        n.setRequiresSignatureZone(true);
                        n.setRequiresStampZone(false);
                        n.setRequiresDestinataire(true);
                        n.setActif(true);
                        return typeDocRepo.save(n);
                    });

            // ── 4. WorkflowDefinition ─────────────────────────────────────
            WorkflowDefinition wf = new WorkflowDefinition();
            wf.setCode("ORDRE_MISSION_WF");
            wf.setLibelle("Circuit Ordre de Mission");
            wf.setDescription("Rédaction → Visa Chef de Service → Signature DG");
            wf.setActif(true);
            wf.setVersion(1);
            wf = wfDefRepo.save(wf);

            // ── 5. Étapes ─────────────────────────────────────────────────

            // Étape 4 créée en premier pour permettre les références nextStepId
            WorkflowStep stepEnd = new WorkflowStep();
            stepEnd.setWorkflowId(wf.getId());
            stepEnd.setOrdre(3);
            stepEnd.setCode("END");
            stepEnd.setLibelle("Ordre de mission finalisé");
            stepEnd.setStepType(StepType.END);
            stepEnd = wfStepRepo.save(stepEnd);

            // Étape 3 — SIGNATURE DG
            WorkflowStep stepSign = new WorkflowStep();
            stepSign.setWorkflowId(wf.getId());
            stepSign.setOrdre(2);
            stepSign.setCode("SIGNATURE_DG");
            stepSign.setLibelle("Signature du Directeur Général");
            stepSign.setStepType(StepType.SIGNATURE);
            stepSign.setAutoTransition(false);
            stepSign.setNextStepId(stepEnd.getId());
            stepSign = wfStepRepo.save(stepSign);

            // Participant SIGNATURE : DG = SIGNATAIRE
            WorkflowParticipant partSign = new WorkflowParticipant();
            partSign.setWorkflowStepId(stepSign.getId());
            partSign.setPosteId(posteDg.getId());
            partSign.setRoleParticipant(RoleParticipant.SIGNATAIRE);
            partSign.setObligatoire(true);
            partSign.setOrdre(0);
            wfPartRepo.save(partSign);

            // Étape 2 — INSTRUCTION (Visa Chef de Service)
            WorkflowStep stepVisa = new WorkflowStep();
            stepVisa.setWorkflowId(wf.getId());
            stepVisa.setOrdre(1);
            stepVisa.setCode("VISA_CHEF");
            stepVisa.setLibelle("Visa du Chef de Service");
            stepVisa.setStepType(StepType.INSTRUCTION);
            stepVisa.setAutoTransition(false);
            stepVisa.setNextStepId(stepSign.getId());
            stepVisa = wfStepRepo.save(stepVisa);

            // Participant VISA : Chef de Service = VALIDATEUR
            WorkflowParticipant partVisa = new WorkflowParticipant();
            partVisa.setWorkflowStepId(stepVisa.getId());
            partVisa.setPosteId(posteChef.getId());
            partVisa.setRoleParticipant(RoleParticipant.VALIDATEUR);
            partVisa.setObligatoire(true);
            partVisa.setOrdre(0);
            wfPartRepo.save(partVisa);

            // Étape 1 — DOCUMENT_CREATION
            WorkflowStep stepDoc = new WorkflowStep();
            stepDoc.setWorkflowId(wf.getId());
            stepDoc.setOrdre(0);
            stepDoc.setCode("DOC_CREATION");
            stepDoc.setLibelle("Rédaction de l'ordre de mission");
            stepDoc.setStepType(StepType.DOCUMENT_CREATION);
            stepDoc.setAutoTransition(true);
            stepDoc.setNextStepId(stepVisa.getId());
            wfStepRepo.save(stepDoc);

            // ── 6. Lier le TypeDocument au Workflow ───────────────────────
            td.setWorkflowDefinitionId(wf.getId());
            typeDocRepo.save(td);

            System.out.println("\n✅ [WorkflowTestData] Ordre de Mission workflow initialisé :");
            System.out.println("   TypeDocument : ORDRE_MISSION (id=" + td.getId() + ")");
            System.out.println("   Workflow     : ORDRE_MISSION_WF (id=" + wf.getId() + ")");
            System.out.println("   Étapes       : DOC_CREATION → VISA_CHEF → SIGNATURE_DG → END");
            System.out.println("   Postes       : DIRECTEUR_GENERAL / SECRETARIAT_DG / CHEF_SERVICE");
            System.out.println("   Utilisateurs : dg → DG | secretaire → Secrétariat | agent.douane → Chef de Service\n");
        };
    }

    private void assignPostesToUsers(PosteRepository posteRepo, AppUserRepository userRepo) {
        posteRepo.findByCode("DIRECTEUR_GENERAL").ifPresent(p -> setPoste(userRepo, "dg", p));
        posteRepo.findByCode("SECRETARIAT_DG").ifPresent(p -> setPoste(userRepo, "secretaire", p));
        posteRepo.findByCode("CHEF_SERVICE").ifPresent(p -> setPoste(userRepo, "agent.douane", p));
    }

    private Poste upsertPoste(PosteRepository repo, String code, String libelle,
                               Set<Poste.Habilitation> habilitations) {
        return repo.findByCode(code)
                .orElseGet(() -> {
                    Poste p = new Poste();
                    p.setCode(code);
                    p.setLibelle(libelle);
                    p.setHabilitations(habilitations);
                    p.setActif(true);
                    return repo.save(p);
                });
    }

    private void setPoste(AppUserRepository userRepo, String username, Poste poste) {
        userRepo.findByUsername(username).ifPresent(u -> {
            if (u.getPoste() == null || !poste.getId().equals(u.getPoste().getId())) {
                u.setPoste(poste);
                userRepo.save(u);
            }
        });
    }
}
