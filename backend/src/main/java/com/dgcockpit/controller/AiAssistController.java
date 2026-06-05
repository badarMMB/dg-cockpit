package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.BureauDocument;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.entity.Poste;
import com.dgcockpit.repository.BureauDocumentRepository;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import com.dgcockpit.service.AiAssistService;
import com.dgcockpit.service.MinioService;
import com.dgcockpit.service.WopiTokenService;
import com.dgcockpit.sse.SseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Endpoint de l'assistant IA de rédaction documentaire.
 *
 * POST /api/ai/assist/{bureauDocumentId}?action=IMPROVE_STYLE|CORRECT_GRAMMAR|SUMMARIZE
 *   → Mode AUTO-APPLY (ai.llm.auto-apply=true)  : réécrit le .docx dans MinIO + SSE RELOAD_IFRAME
 *   → Mode SUGGESTION (ai.llm.auto-apply=false) : poste une suggestion dans le fil d'instruction
 *
 * POST /api/ai/suggest/{bureauDocumentId}?action=…
 *   → Force toujours le mode SUGGESTION, quel que soit ai.llm.auto-apply.
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiAssistController {

    private final BureauDocumentRepository docRepo;
    private final MinioService minio;
    private final AiAssistService aiService;
    private final SseService sse;
    private final WopiTokenService wopiTokenService;
    private final InstructionRepository instrRepo;
    private final InstructionMessageRepository msgRepo;

    public AiAssistController(BureauDocumentRepository docRepo,
                              MinioService minio,
                              AiAssistService aiService,
                              SseService sse,
                              WopiTokenService wopiTokenService,
                              InstructionRepository instrRepo,
                              InstructionMessageRepository msgRepo) {
        this.docRepo          = docRepo;
        this.minio            = minio;
        this.aiService        = aiService;
        this.sse              = sse;
        this.wopiTokenService = wopiTokenService;
        this.instrRepo        = instrRepo;
        this.msgRepo          = msgRepo;
    }

    /** Assist : mode AUTO-APPLY ou SUGGESTION selon configuration. */
    @PostMapping("/assist/{bureauDocumentId}")
    public ResponseEntity<Map<String, Object>> assist(
            @PathVariable String bureauDocumentId,
            @RequestParam String action,
            HttpServletRequest req) throws Exception {

        AppUser current = (AppUser) req.getAttribute("currentUser");
        BureauDocument doc = docRepo.findById(bureauDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable"));

        checkAccess(current, doc);
        if (!aiService.isEnabled())
            return ResponseEntity.status(503)
                    .body(Map.of("error", "L'assistant IA n'est pas configuré (clé API manquante)."));

        validateAction(action);

        if (aiService.isAutoApply()) {
            return applyToDoc(doc, action, current);
        } else {
            return suggestToThread(doc, action, current);
        }
    }

    /** Suggest : toujours en mode suggestion (poste dans le fil, ne touche pas au fichier). */
    @PostMapping("/suggest/{bureauDocumentId}")
    public ResponseEntity<Map<String, Object>> suggest(
            @PathVariable String bureauDocumentId,
            @RequestParam String action,
            HttpServletRequest req) throws Exception {

        AppUser current = (AppUser) req.getAttribute("currentUser");
        BureauDocument doc = docRepo.findById(bureauDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable"));

        checkAccess(current, doc);
        if (!aiService.isEnabled())
            return ResponseEntity.status(503)
                    .body(Map.of("error", "L'assistant IA n'est pas configuré (clé API manquante)."));

        validateAction(action);
        return suggestToThread(doc, action, current);
    }

    // ── Modes ──────────────────────────────────────────────────────────────

    /**
     * Mode AUTO-APPLY : l'IA réécrit le .docx dans MinIO.
     * Ré-injection non destructive (POI, préserve styles + signets).
     * Après écriture : invalide les WopiTokens (force rechargement) + SSE RELOAD_IFRAME.
     */
    private ResponseEntity<Map<String, Object>> applyToDoc(BureauDocument doc,
                                                            String action,
                                                            AppUser editor) throws Exception {
        byte[] docxBytes = minio.downloadBytes(doc.getBucket(), doc.getObjectKey());

        // Extraction des paragraphes indexés
        var paragraphs = aiService.extractParagraphs(docxBytes);
        if (paragraphs.isEmpty())
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Le document ne contient pas de texte extractible."));

        // Appel LLM
        Map<Integer, String> improved = aiService.callLlmIndexed(action, paragraphs);

        // Ré-injection non destructive (ne touche pas aux signets ni aux styles)
        byte[] modifiedDocx = aiService.applyParagraphReplacements(docxBytes, improved);

        // Écraser dans MinIO
        minio.uploadBytes(doc.getBucket(), doc.getObjectKey(), modifiedDocx,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        doc.setUpdatedAt(LocalDateTime.now());
        docRepo.save(doc);

        // Invalider les tokens WOPI actifs → Collabora rechargera le document
        wopiTokenService.invalidateForDocument(doc.getId());

        // Notifier l'utilisateur via SSE
        Map<String, Object> ssePayload = new LinkedHashMap<>();
        ssePayload.put("bureauDocumentId", doc.getId());
        ssePayload.put("action", action);
        sse.broadcast("RELOAD_IFRAME", ssePayload);

        // Message système dans le fil si lié à une instruction
        if (doc.getSourceInstructionId() != null) {
            ajouterMessageSysteme(doc.getSourceInstructionId(),
                    "🤖 Document amélioré par l'IA (" + actionLabel(action) + ") par "
                            + editor.getNomComplet());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "AUTO_APPLY");
        result.put("action", action);
        result.put("paragraphsModified", improved.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Mode SUGGESTION : l'IA propose, l'utilisateur décide.
     * La suggestion est postée comme message système dans le fil d'instruction.
     * Si le document n'est pas lié à une instruction, la suggestion est retournée
     * directement dans la réponse HTTP pour affichage côté frontend.
     */
    private ResponseEntity<Map<String, Object>> suggestToThread(BureauDocument doc,
                                                                  String action,
                                                                  AppUser user) throws Exception {
        byte[] docxBytes = minio.downloadBytes(doc.getBucket(), doc.getObjectKey());

        String fullText = aiService.extractFullText(docxBytes);
        if (fullText.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Le document ne contient pas de texte extractible."));

        String suggestion = aiService.callLlmFullText(action, fullText);

        // Tronquer pour le message (max 2000 caractères pour lisibilité dans le chat)
        String preview = suggestion.length() > 2000
                ? suggestion.substring(0, 2000) + "…"
                : suggestion;

        String messageText = "🤖 Suggestion IA (" + actionLabel(action) + ") :\n\n" + preview;

        if (doc.getSourceInstructionId() != null) {
            ajouterMessageSysteme(doc.getSourceInstructionId(), messageText);
            sse.broadcast("INSTRUCTION_UPDATED",
                    Map.of("id", doc.getSourceInstructionId()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "SUGGESTION");
        result.put("action", action);
        result.put("suggestion", suggestion);
        result.put("postedToInstruction", doc.getSourceInstructionId() != null);
        return ResponseEntity.ok(result);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void checkAccess(AppUser user, BureauDocument doc) {
        if (user == null) throw new com.dgcockpit.exception.AccesRefuseException("Non authentifié");
        boolean isOwner = user.getId().equals(doc.getProprietaireId());
        boolean isAdmin = user.hasHabilitation(Poste.Habilitation.CAN_VIEW_ALL);
        if (!isOwner && !isAdmin)
            throw new com.dgcockpit.exception.AccesRefuseException("Non autorisé");
    }

    private void validateAction(String action) {
        if (!Set.of("IMPROVE_STYLE", "CORRECT_GRAMMAR", "SUMMARIZE").contains(action))
            throw new IllegalArgumentException("Action IA invalide : " + action);
    }

    private String actionLabel(String action) {
        return switch (action) {
            case "IMPROVE_STYLE"   -> "style administratif";
            case "CORRECT_GRAMMAR" -> "correction grammaticale";
            case "SUMMARIZE"       -> "résumé objet";
            default -> action;
        };
    }

    private void ajouterMessageSysteme(String instructionId, String texte) {
        instrRepo.findById(instructionId).ifPresent(instr -> {
            if (instr.getStatut() != Instruction.StatutInstruction.CLOTURE) {
                InstructionMessage msg = new InstructionMessage();
                msg.setInstruction(instr);
                msg.setSender("Système");
                msg.setSelf(false);
                msg.setSystemMessage(true);
                msg.setText(texte);
                msgRepo.save(msg);
            }
        });
    }
}
