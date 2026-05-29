package com.dgcockpit.service;

import com.dgcockpit.entity.*;
import com.dgcockpit.exception.AccesRefuseException;
import com.dgcockpit.repository.*;
import com.dgcockpit.sse.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Moteur de workflow : orchestre la progression d'une instruction à travers
 * les étapes de circuit définies par son {@link InstructionType}.
 * <p>
 * Invariants métier :
 * <ul>
 *   <li>Seul l'utilisateur dont le {@link Poste} correspond au {@link WorkflowStep#getRequiredPoste()}
 *       peut valider ou rejeter l'étape courante.</li>
 *   <li>Une instruction en BROUILLON doit être explicitement lancée avant toute validation.</li>
 *   <li>Un dossier clôturé (CLOTURE_VALIDE ou CLOTURE_REJETE) ne peut plus être modifié.</li>
 *   <li>Chaque transition déclenche un événement SSE {@code INSTRUCTION_UPDATED} et
 *       enregistre un message système dans le fil de conversation.</li>
 * </ul>
 */
@Service
@Transactional
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final InstructionRepository instructionRepo;
    private final WorkflowStepRepository workflowStepRepo;
    private final InstructionMessageRepository messageRepo;
    private final AppUserRepository userRepo;
    private final UserSignatureAssetRepository assetRepo;
    private final SseService sseService;

    public WorkflowService(InstructionRepository instructionRepo,
                           WorkflowStepRepository workflowStepRepo,
                           InstructionMessageRepository messageRepo,
                           AppUserRepository userRepo,
                           UserSignatureAssetRepository assetRepo,
                           SseService sseService) {
        this.instructionRepo  = instructionRepo;
        this.workflowStepRepo = workflowStepRepo;
        this.messageRepo      = messageRepo;
        this.userRepo         = userRepo;
        this.assetRepo        = assetRepo;
        this.sseService       = sseService;
    }

    // ── Opérations publiques ──────────────────────────────────────────────────

    /**
     * Lance le circuit de validation d'une instruction en brouillon.
     * Passe le statut à {@code EN_CIRCUIT} et positionne la première étape.
     *
     * @throws IllegalStateException si l'instruction n'est pas en BROUILLON
     *                               ou si aucun circuit n'est configuré pour son type
     */
    public Map<String, Object> lancerCircuit(String instructionId, AppUser initiateur) {
        Instruction instruction = chargerInstruction(instructionId);

        if (instruction.getGlobalStatus() != Instruction.GlobalStatus.BROUILLON) {
            throw new IllegalStateException(
                "L'instruction n'est pas en brouillon (statut actuel : "
                + instruction.getGlobalStatus() + ")");
        }
        if (instruction.getInstructionType() == null) {
            throw new IllegalStateException(
                "Impossible de lancer le circuit : aucun type d'instruction défini");
        }

        // Première étape du circuit (stepOrder le plus bas, généralement 0)
        WorkflowStep premiereEtape = workflowStepRepo
            .findFirstByInstructionTypeIdAndStepOrderGreaterThanOrderByStepOrderAsc(
                instruction.getInstructionType().getId(), -1)
            .orElseThrow(() -> new IllegalStateException(
                "Aucun circuit configuré pour le type : "
                + instruction.getInstructionType().getLabel()));

        instruction.setGlobalStatus(Instruction.GlobalStatus.EN_CIRCUIT);
        instruction.setCurrentStep(premiereEtape);
        instruction.setCurrentActor(trouverActeurPourEtape(premiereEtape));
        synchroniserStatutLegacy(instruction);

        enregistrerMessageSysteme(instruction, initiateur,
            "🚀 Circuit lancé — Étape en cours : " + premiereEtape.getStepLabel()
            + afficherActeurAttendu(instruction.getCurrentStep()),
            premiereEtape);

        instructionRepo.save(instruction);
        emettreMiseAJour(instruction);
        log.info("[Workflow] Circuit lancé — instruction={} type={} étape={}",
            instructionId, instruction.getInstructionType().getLabel(),
            premiereEtape.getStepLabel());
        return toEtatDto(instruction);
    }

    /**
     * Valide l'étape courante pour l'acteur donné.
     * <ol>
     *   <li>Vérifie que l'instruction est en circuit actif.</li>
     *   <li>Contrôle les droits via {@link AppUser#peutAgirSurEtape(WorkflowStep)}.</li>
     *   <li>Applique la signature si {@link WorkflowStep#isRequiresSignature()} est vrai.</li>
     *   <li>Progresse vers l'étape suivante ou clôture positivement si c'était la dernière.</li>
     * </ol>
     *
     * @throws AccessDeniedException si le Poste de l'acteur ne correspond pas à l'étape
     * @throws IllegalStateException si l'instruction n'est pas en circuit
     */
    public Map<String, Object> validerEtapeActuelle(String instructionId, AppUser acteurCourant) {
        Instruction instruction = chargerInstruction(instructionId);

        verifierCircuitActif(instruction);
        verifierDroitsActeur(acteurCourant, instruction.getCurrentStep());

        WorkflowStep etapeCourante = instruction.getCurrentStep();

        // Appliquer la signature si l'étape le requiert
        if (etapeCourante.isRequiresSignature()) {
            appliquerSignature(instruction, acteurCourant, etapeCourante);
        }

        // Chercher l'étape suivante dans le circuit
        Optional<WorkflowStep> etapeSuivante = workflowStepRepo
            .findFirstByInstructionTypeIdAndStepOrderGreaterThanOrderByStepOrderAsc(
                instruction.getInstructionType().getId(),
                etapeCourante.getStepOrder());

        if (etapeSuivante.isPresent()) {
            // Progression vers l'étape suivante
            WorkflowStep prochaine = etapeSuivante.get();
            instruction.setCurrentStep(prochaine);
            instruction.setCurrentActor(trouverActeurPourEtape(prochaine));

            enregistrerMessageSysteme(instruction, acteurCourant,
                "✅ Étape validée : " + etapeCourante.getStepLabel()
                + " → Prochaine étape : " + prochaine.getStepLabel()
                + afficherActeurAttendu(prochaine),
                prochaine);

            log.info("[Workflow] Étape validée — instruction={} étape={} → {}", instructionId,
                etapeCourante.getStepLabel(), prochaine.getStepLabel());
        } else {
            // Dernière étape : clôture positive
            instruction.setGlobalStatus(Instruction.GlobalStatus.CLOTURE_VALIDE);
            instruction.setCurrentStep(null);
            instruction.setCurrentActor(null);

            enregistrerMessageSysteme(instruction, acteurCourant,
                "🏁 Circuit complété — Dossier clôturé positivement après validation de : "
                + etapeCourante.getStepLabel(),
                null);

            log.info("[Workflow] Circuit terminé positivement — instruction={}", instructionId);
        }

        synchroniserStatutLegacy(instruction);
        instructionRepo.save(instruction);
        emettreMiseAJour(instruction);
        return toEtatDto(instruction);
    }

    /**
     * Rejette l'étape courante et clôture le dossier négativement ({@code CLOTURE_REJETE}).
     *
     * @param motif raison du rejet (affiché dans le message système, peut être null)
     * @throws AccessDeniedException si le Poste de l'acteur ne correspond pas à l'étape
     * @throws IllegalStateException si l'instruction n'est pas en circuit
     */
    public Map<String, Object> rejeterEtapeActuelle(String instructionId,
                                                    AppUser acteurCourant,
                                                    String motif) {
        Instruction instruction = chargerInstruction(instructionId);

        verifierCircuitActif(instruction);
        verifierDroitsActeur(acteurCourant, instruction.getCurrentStep());

        WorkflowStep etapeCourante = instruction.getCurrentStep();
        String texteRejet = "❌ Étape rejetée : " + etapeCourante.getStepLabel()
            + (motif != null && !motif.isBlank() ? " — Motif : " + motif : "");

        enregistrerMessageSysteme(instruction, acteurCourant, texteRejet, null);

        instruction.setGlobalStatus(Instruction.GlobalStatus.CLOTURE_REJETE);
        instruction.setCurrentStep(null);
        instruction.setCurrentActor(null);
        synchroniserStatutLegacy(instruction);

        instructionRepo.save(instruction);
        emettreMiseAJour(instruction);
        log.info("[Workflow] Étape rejetée — instruction={} étape={} motif={}",
            instructionId, etapeCourante.getStepLabel(), motif);
        return toEtatDto(instruction);
    }

    // ── Méthodes privées ──────────────────────────────────────────────────────

    private Instruction chargerInstruction(String id) {
        return instructionRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Instruction introuvable : " + id));
    }

    private void verifierCircuitActif(Instruction instruction) {
        if (instruction.getGlobalStatus() == Instruction.GlobalStatus.BROUILLON) {
            throw new IllegalStateException(
                "Le circuit n'a pas encore été lancé pour ce dossier");
        }
        if (instruction.getGlobalStatus() != Instruction.GlobalStatus.EN_CIRCUIT) {
            throw new IllegalStateException(
                "Le dossier est déjà clôturé (statut : " + instruction.getGlobalStatus() + ")");
        }
        if (instruction.getCurrentStep() == null) {
            throw new IllegalStateException(
                "Aucune étape courante définie — état incohérent pour l'instruction "
                + instruction.getId());
        }
    }

    private void verifierDroitsActeur(AppUser acteur, WorkflowStep etape) {
        if (!acteur.peutAgirSurEtape(etape)) {
            String posteActeur = acteur.getPoste() != null
                ? acteur.getPoste().getLibelle() : "non défini";
            String posteRequis = etape.getRequiredPoste() != null
                ? etape.getRequiredPoste().getLibelle() : "tout poste";
            throw new AccesRefuseException(
                "Votre poste (%s) n'est pas habilité à agir sur cette étape (requis : %s)"
                .formatted(posteActeur, posteRequis));
        }
    }

    /**
     * Tente d'associer la signature numérique de l'acteur au document joint.
     * <p>
     * Note d'architecture : l'incrustation complète (avec zones positionnées) nécessite
     * que le document soit enregistré dans le parapheur ({@link com.dgcockpit.entity.PdfDocument})
     * avec des annotations de zones ({@link com.dgcockpit.entity.PageAnnotation}) définies par l'acteur.
     * Le workflow note la signature requise et délègue l'apposition au parapheur.
     */
    private void appliquerSignature(Instruction instruction, AppUser acteur, WorkflowStep etape) {
        List<UserSignatureAsset> assets = assetRepo.findByUserIdAndActiveTrue(acteur.getId());
        UserSignatureAsset signature = assets.stream()
            .filter(a -> "SIGNATURE".equals(a.getAssetType()))
            .findFirst()
            .orElse(null);

        if (signature == null) {
            log.warn("[Workflow] Aucun asset de signature actif pour {} — étape '{}' validée sans incrustation",
                acteur.getNomComplet(), etape.getStepLabel());
            return;
        }

        // Signature trouvée : l'incrustation dans le PDF se fait via le parapheur.
        // Le document doit transiter par l'interface de signature pour positionner les zones.
        log.info("[Workflow] Asset de signature {} trouvé pour {} — délégation au parapheur pour l'instruction {}",
            signature.getId(), acteur.getNomComplet(), instruction.getId());
    }

    /** Cherche le premier utilisateur actif possédant le Poste requis par l'étape. */
    private AppUser trouverActeurPourEtape(WorkflowStep etape) {
        if (etape == null || etape.getRequiredPoste() == null) return null;
        return userRepo.findByPosteIdAndActifTrue(etape.getRequiredPoste().getId())
            .stream().findFirst().orElse(null);
    }

    /** Construit le texte d'indication de l'acteur attendu pour les messages système. */
    private String afficherActeurAttendu(WorkflowStep etape) {
        if (etape == null || etape.getRequiredPoste() == null) return "";
        return " (en attente : " + etape.getRequiredPoste().getLibelle() + ")";
    }

    /** Enregistre un message système dans le fil de conversation de l'instruction. */
    private void enregistrerMessageSysteme(Instruction instruction, AppUser auteur,
                                           String texte, WorkflowStep etapeContexte) {
        InstructionMessage msg = new InstructionMessage();
        msg.setInstruction(instruction);
        msg.setSender(auteur.getNomComplet());
        msg.setSelf(false);
        msg.setType(InstructionMessage.TypeMessage.SYSTEM);
        msg.setText(texte);
        msg.setWorkflowStep(etapeContexte);
        messageRepo.save(msg);
    }

    /**
     * Maintient le champ {@code statut} déprécié synchronisé avec {@code globalStatus}
     * afin que les contrôleurs hérités continuent de fonctionner pendant la migration.
     */
    @SuppressWarnings({"deprecation", "java:S1874"})
    private void synchroniserStatutLegacy(Instruction instruction) {
        Instruction.StatutInstruction legacy = switch (instruction.getGlobalStatus()) {
            case BROUILLON      -> Instruction.StatutInstruction.OUVERT;
            case EN_CIRCUIT     -> Instruction.StatutInstruction.EN_COURS;
            case CLOTURE_VALIDE -> Instruction.StatutInstruction.CLOTURE;
            case CLOTURE_REJETE -> Instruction.StatutInstruction.REFUSE;
        };
        instruction.setStatut(legacy);
    }

    /** Émet l'événement SSE {@code INSTRUCTION_UPDATED} vers tous les clients connectés. */
    @SuppressWarnings({"deprecation", "java:S1874"})
    private void emettreMiseAJour(Instruction instruction) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id",           instruction.getId());
        payload.put("globalStatus", instruction.getGlobalStatus().name());
        payload.put("statut",       instruction.getStatut() != null
                                    ? instruction.getStatut().name() : "CLOTURE");
        if (instruction.getCurrentStep() != null) {
            payload.put("currentStepLabel", instruction.getCurrentStep().getStepLabel());
            payload.put("currentStepOrder", instruction.getCurrentStep().getStepOrder());
            if (instruction.getCurrentStep().getRequiredPoste() != null) {
                payload.put("requiredPosteId",
                    instruction.getCurrentStep().getRequiredPoste().getId());
            }
        }
        sseService.broadcast("INSTRUCTION_UPDATED", payload);
    }

    // ── DTO publics ───────────────────────────────────────────────────────────

    /** Construit la représentation JSON de l'état courant du workflow. */
    public Map<String, Object> toEtatDto(Instruction instruction) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",           instruction.getId());
        dto.put("globalStatus", instruction.getGlobalStatus().name());
        dto.put("currentStep",  buildStepDto(instruction.getCurrentStep()));
        dto.put("currentActor", buildActeurDto(instruction.getCurrentActor()));
        return dto;
    }

    private Map<String, Object> buildStepDto(WorkflowStep step) {
        if (step == null) return null;
        Map<String, Object> s = new HashMap<>();
        s.put("id",                  step.getId());
        s.put("stepOrder",           step.getStepOrder());
        s.put("stepLabel",           step.getStepLabel());
        s.put("requiresSignature",   step.isRequiresSignature());
        s.put("requiresAttachment",  step.isRequiresAttachment());
        s.put("actorInstructions",   step.getActorInstructions());
        s.put("requiredPoste",       buildPosteDto(step.getRequiredPoste()));
        return s;
    }

    private Map<String, Object> buildPosteDto(Poste poste) {
        if (poste == null) return null;
        return Map.of("id", poste.getId(), "code", poste.getCode(), "libelle", poste.getLibelle());
    }

    private Map<String, Object> buildActeurDto(AppUser acteur) {
        if (acteur == null) return null;
        return Map.of("id", acteur.getId(), "nomComplet", acteur.getNomComplet());
    }
}
