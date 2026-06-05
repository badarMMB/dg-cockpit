package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.BureauDocument;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.entity.PageAnnotation;
import com.dgcockpit.entity.PdfDocument;
import com.dgcockpit.entity.WopiToken;
import com.dgcockpit.repository.AppUserRepository;
import com.dgcockpit.repository.BureauDocumentRepository;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import com.dgcockpit.repository.PageAnnotationRepository;
import com.dgcockpit.repository.PdfDocumentRepository;
import com.dgcockpit.service.CollaboraConvertService;
import com.dgcockpit.service.DocumentFinalizationService;
import com.dgcockpit.service.MinioService;
import com.dgcockpit.service.WopiTokenService;
import com.dgcockpit.sse.SseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implémentation du protocole WOPI (Web Application Open Platform Interface)
 * consommé par Collabora Online pour éditer les fichiers .docx in-browser.
 *
 * <p><b>Sécurité :</b> ces endpoints sont exemptés du filtre JWT (cf. AuthFilter
 * et SecurityConfig). Collabora les appelle server-to-server avec un
 * {@code access_token} en query param, validé par {@link WopiTokenService}.</p>
 *
 * <p>Le format MIME .docx est :
 * {@code application/vnd.openxmlformats-officedocument.wordprocessingml.document}.</p>
 */
@RestController
@RequestMapping("/api/wopi/files")
@org.springframework.web.bind.annotation.CrossOrigin(origins = "*")
public class WopiController {

    private static final String DOCX_MIME =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final WopiTokenService tokenService;
    private final BureauDocumentRepository docRepo;
    private final AppUserRepository userRepo;
    private final MinioService minio;
    private final InstructionRepository instrRepo;
    private final InstructionMessageRepository msgRepo;
    private final SseService sse;
    private final CollaboraConvertService collaboraConvert;
    private final DocumentFinalizationService finalizer;
    private final PdfDocumentRepository pdfRepo;
    private final PageAnnotationRepository annotRepo;
    private final ObjectMapper objectMapper;

    public WopiController(WopiTokenService tokenService,
                          BureauDocumentRepository docRepo,
                          AppUserRepository userRepo,
                          MinioService minio,
                          InstructionRepository instrRepo,
                          InstructionMessageRepository msgRepo,
                          SseService sse,
                          CollaboraConvertService collaboraConvert,
                          DocumentFinalizationService finalizer,
                          PdfDocumentRepository pdfRepo,
                          PageAnnotationRepository annotRepo,
                          ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.docRepo = docRepo;
        this.userRepo = userRepo;
        this.minio = minio;
        this.instrRepo = instrRepo;
        this.msgRepo = msgRepo;
        this.sse = sse;
        this.collaboraConvert = collaboraConvert;
        this.finalizer = finalizer;
        this.pdfRepo = pdfRepo;
        this.annotRepo = annotRepo;
        this.objectMapper = objectMapper;
    }

    // ── (1) CheckFileInfo — GET /api/wopi/files/{id}?access_token=… ──────────
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> checkFileInfo(
            @PathVariable String id,
            @RequestParam("access_token") String token) {

        WopiToken t = tokenService.validate(token, id);
        BureauDocument doc = docRepo.findById(id).orElseThrow();
        AppUser user = userRepo.findById(t.getUserId()).orElseThrow();

        long size = minio.sizeOf(doc.getBucket(), doc.getObjectKey());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("BaseFileName", doc.getOriginalFileName());
        info.put("Size", size);
        info.put("OwnerId", doc.getProprietaireId());
        info.put("UserId", user.getId());
        info.put("UserFriendlyName", user.getNomComplet());
        info.put("UserCanWrite", t.isCanWrite());
        info.put("UserCanNotWriteRelative", true);   // interdit Save As / renommage
        info.put("DisableExport", !t.isCanWrite());
        info.put("DisablePrint", false);
        info.put("PostMessageOrigin", "*");
        info.put("SupportsUpdate", true);
        info.put("SupportsLocks", false);
        info.put("LastModifiedTime", doc.getUpdatedAt().toString());
        // Activer les commentaires natifs Collabora (margin comments, comme Word)
        // et le suivi des modifications (Track Changes) — le DG les utilise pour
        // annoter le .docx avant renvoi pour correction, sans passer par les highlights pixel.
        info.put("AllowComments", t.isCanWrite());
        info.put("AllowEditComment", t.isCanWrite());
        return ResponseEntity.ok(info);
    }

    // ── (2) GetFile — GET /api/wopi/files/{id}/contents?access_token=… ──────
    @GetMapping("/{id}/contents")
    public ResponseEntity<byte[]> getFile(
            @PathVariable String id,
            @RequestParam("access_token") String token) throws Exception {

        tokenService.validate(token, id);
        BureauDocument doc = docRepo.findById(id).orElseThrow();
        byte[] data = minio.downloadBytes(doc.getBucket(), doc.getObjectKey());

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(DOCX_MIME))
            .contentLength(data.length)
            .body(data);
    }

    // ── (3) PutFile — POST /api/wopi/files/{id}/contents?access_token=… ─────
    @PostMapping("/{id}/contents")
    public ResponseEntity<Map<String, Object>> putFile(
            @PathVariable String id,
            @RequestParam("access_token") String token,
            HttpServletRequest request) throws Exception {

        WopiToken t = tokenService.validate(token, id);
        if (!t.isCanWrite()) {
            // Code WOPI "lecture seule" — Collabora cessera de tenter la sauvegarde
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("LOOLStatusCode", 1010));
        }

        BureauDocument doc = docRepo.findById(id).orElseThrow();
        byte[] body = request.getInputStream().readAllBytes();

        // Archive versionnée de l'état courant avant écrasement (best-effort)
        try {
            byte[] current = minio.downloadBytes(doc.getBucket(), doc.getObjectKey());
            String archiveKey = doc.getObjectKey() + "_v_" + System.currentTimeMillis();
            minio.uploadBytes(doc.getBucket(), archiveKey, current, DOCX_MIME);
        } catch (Exception ignored) {
            // pas de version précédente = OK (premier enregistrement)
        }

        // Écrase la version courante
        minio.uploadBytes(doc.getBucket(), doc.getObjectKey(), body, DOCX_MIME);
        doc.setUpdatedAt(LocalDateTime.now());
        // Pour les documents RETOURNÉS, la modification via Collabora équivaut à une correction
        if (doc.getStatut() == BureauDocument.Statut.RETOURNE) {
            doc.setCorrigeDepuisRenvoi(true);
        }

        // ── Régénération automatique du PDF de signature depuis le .docx ────────
        // Le placement des zones (bureau) et la signature (PDFBox) se font sur ce PDF.
        if (doc.getOriginalFileName() != null
                && doc.getOriginalFileName().toLowerCase().endsWith(".docx")) {
            try {
                byte[] pdfBytes = collaboraConvert.docxToPdf(body, doc.getOriginalFileName());
                String pdfKey = doc.getSignaturePdfKey() != null
                    ? doc.getSignaturePdfKey()   // réécrit la même clé (idempotent, MinIO propre)
                    : UUID.randomUUID() + "_" + doc.getOriginalFileName().replaceAll("\\.docx$", ".pdf");
                minio.uploadBytes("ged-documents", pdfKey, pdfBytes, "application/pdf");
                int newPages = finalizer.getPageCount("ged-documents", pdfKey);

                // Si la pagination change, les zones placées deviennent obsolètes → reset
                if (doc.getPageCount() != null && doc.getPageCount() != newPages) {
                    doc.setSignatureZonesJson(null);
                    doc.setStampZonesJson(null);
                }
                doc.setSignaturePdfKey(pdfKey);
                doc.setPageCount(newPages);

                // Si le document est déjà soumis et en attente de signature, re-synchroniser
                // le PdfDocument signé pour que les corrections du DG apparaissent au final.
                resyncPdfDocument(doc, pdfKey, newPages);
            } catch (Exception ignored) {
                // conversion best-effort : ne jamais faire échouer la sauvegarde Collabora
            }
        }

        docRepo.save(doc);

        // Notifie le fil d'instruction si le document y est rattaché —
        // une seule fois par session WOPI (premier PutFile après émission du token).
        // Les autosaves suivants (~10s) restent silencieux pour éviter le flood.
        if (doc.getSourceInstructionId() != null) {
            AppUser editor = userRepo.findById(t.getUserId()).orElse(null);
            String nom = editor != null ? editor.getNomComplet() : "un utilisateur";
            // updatedAt est mis à jour juste avant docRepo.save() — on le compare
            // à la date d'émission du token (+ 10s de tolérance pour la 1ère sauvegarde).
            Instant tokenCreated = t.getCreatedAt();
            Instant docPreviousUpdate = doc.getUpdatedAt() != null
                ? doc.getUpdatedAt().toInstant(java.time.ZoneOffset.UTC)
                : Instant.EPOCH;
            boolean isFirstSave = docPreviousUpdate.isBefore(tokenCreated.plusSeconds(10));
            if (isFirstSave) {
                ajouterMessageSysteme(doc.getSourceInstructionId(),
                    "✏️ Document modifié par " + nom);
            }
        }

        return ResponseEntity.ok(Map.of("LastModifiedTime", doc.getUpdatedAt().toString()));
    }

    /**
     * Re-synchronise le PdfDocument signé avec le PDF fraîchement régénéré depuis le .docx,
     * uniquement si le document est déjà soumis et en attente de signature.
     * Les PageAnnotation sont recréées depuis les zones du BureauDocument (inchangées).
     */
    private void resyncPdfDocument(BureauDocument doc, String pdfKey, int newPages) {
        if (doc.getPdfDocumentId() == null) return;
        PdfDocument pdf = pdfRepo.findById(doc.getPdfDocumentId()).orElse(null);
        if (pdf == null
                || pdf.getParapheurStatut() != PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE) {
            return;
        }

        // Pointer le PdfDocument vers le PDF régénéré
        pdf.setBucket("ged-documents");
        pdf.setObjectKey(pdfKey);
        pdf.setPageCount(newPages);
        pdf.setUpdatedAt(LocalDateTime.now());
        pdfRepo.save(pdf);

        // Purger les anciennes annotations puis recréer depuis les zones du bureau
        for (int p = 0; p < newPages; p++) {
            annotRepo.deleteByPageId(pdf.getId() + "::" + p);
        }
        creerAnnotations(pdf.getId(), parseZones(doc.getSignatureZonesJson()), "SIGNATURE_ZONE");
        creerAnnotations(pdf.getId(), parseZones(doc.getStampZonesJson()), "STAMP_ZONE");
    }

    private void creerAnnotations(String pdfDocId, List<Map<String, Object>> zones, String type) {
        for (Map<String, Object> z : zones) {
            PageAnnotation a = new PageAnnotation();
            a.setPageId(pdfDocId + "::" + toInt(z.get("page")));
            a.setAnnotationType(type);
            a.setXPercent(toDouble(z.get("x")));
            a.setYPercent(toDouble(z.get("y")));
            a.setWidthPercent(toDouble(z.get("w")));
            a.setHeightPercent(toDouble(z.get("h")));
            a.setCreatedBy("DG-resync");
            annotRepo.save(a);
        }
    }

    private List<Map<String, Object>> parseZones(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private double toDouble(Object v) { return v instanceof Number n ? n.doubleValue() : 0.0; }
    private int toInt(Object v) { return v instanceof Number n ? n.intValue() : 0; }

    /** Injecte un message système dans le fil d'une instruction (sans la clôturer). */
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
                sse.broadcast("INSTRUCTION_UPDATED", Map.of(
                    "id", instructionId, "statut", instr.getStatut().name()));
            }
        });
    }
}
