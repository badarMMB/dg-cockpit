package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.BureauDocument;
import com.dgcockpit.entity.CircuitSignature;
import com.dgcockpit.entity.CourrierDepart;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.entity.PageAnnotation;
import com.dgcockpit.entity.PdfDocument;
import com.dgcockpit.entity.UserSignatureAsset;
import com.dgcockpit.repository.BureauDocumentRepository;
import com.dgcockpit.repository.CircuitSignatureRepository;
import com.dgcockpit.repository.CourrierDepartRepository;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import com.dgcockpit.repository.PageAnnotationRepository;
import com.dgcockpit.repository.PdfDocumentRepository;
import com.dgcockpit.repository.UserSignatureAssetRepository;
import com.dgcockpit.service.DocumentFinalizationService;
import com.dgcockpit.service.MinioService;
import com.dgcockpit.sse.SseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/parapheur")
@CrossOrigin(origins = "*")
public class ParapheurController {

    private final PdfDocumentRepository repo;
    private final DocumentFinalizationService finalizer;
    private final MinioService minio;
    private final CourrierDepartRepository courrierDepartRepo;
    private final PageAnnotationRepository annotRepo;
    private final UserSignatureAssetRepository assetRepo;
    private final CircuitSignatureRepository circuitRepo;
    private final BureauDocumentRepository bureauRepo;
    private final InstructionRepository instructionRepo;
    private final InstructionMessageRepository instructionMessageRepo;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    private static final String AUDIO_BUCKET = "ged-audio-corrections";

    public ParapheurController(PdfDocumentRepository repo,
                               DocumentFinalizationService finalizer,
                               MinioService minio,
                               CourrierDepartRepository courrierDepartRepo,
                               PageAnnotationRepository annotRepo,
                               UserSignatureAssetRepository assetRepo,
                               CircuitSignatureRepository circuitRepo,
                               BureauDocumentRepository bureauRepo,
                               InstructionRepository instructionRepo,
                               InstructionMessageRepository instructionMessageRepo,
                               SseService sseService,
                               ObjectMapper objectMapper) {
        this.repo = repo;
        this.finalizer = finalizer;
        this.minio = minio;
        this.courrierDepartRepo = courrierDepartRepo;
        this.annotRepo = annotRepo;
        this.assetRepo = assetRepo;
        this.circuitRepo = circuitRepo;
        this.bureauRepo = bureauRepo;
        this.instructionRepo = instructionRepo;
        this.instructionMessageRepo = instructionMessageRepo;
        this.sseService = sseService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<PdfDocument> pending(HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        if (currentUser == null) return List.of();
        return repo.findByParapheurStatutInAndCurrentSignataireUserIdOrderBySubmittedAtDesc(
                List.of(PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE,
                        PdfDocument.ParapheurStatut.EN_CORRECTION),
                currentUser.getId());
    }

    @GetMapping("/historique")
    public List<PdfDocument> historique() {
        return repo.findHistorique(List.of(
                PdfDocument.ParapheurStatut.SIGNE,
                PdfDocument.ParapheurStatut.REFUSE,
                PdfDocument.ParapheurStatut.PUBLIE,
                PdfDocument.ParapheurStatut.RENVOYE));
    }

    @GetMapping("/notes-de-service")
    public List<PdfDocument> notesDeService() {
        return repo.findByParapheurStatutAndParapheurTypeOrderBySignedAtDesc(
                PdfDocument.ParapheurStatut.PUBLIE, PdfDocument.ParapheurType.NOTE_SERVICE);
    }

    @PostMapping("/soumettre")
    public ResponseEntity<PdfDocument> soumettre(
            @RequestParam MultipartFile file,
            @RequestParam String title,
            @RequestParam String parapheurType,
            @RequestParam(required = false) String destinataire,
            HttpServletRequest request) throws Exception {

        String objectKey = UUID.randomUUID() + "_" + file.getOriginalFilename();
        minio.uploadBytes("ged-documents", objectKey, file.getBytes(), "application/pdf");

        int pageCount;
        try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
            pageCount = pdf.getNumberOfPages();
        }

        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        String submittedBy = currentUser != null ? currentUser.getUsername() : "secretaire";

        PdfDocument doc = new PdfDocument();
        doc.setTitle(title);
        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setBucket("ged-documents");
        doc.setObjectKey(objectKey);
        doc.setPageCount(pageCount);
        doc.setStatus("DRAFT");
        doc.setParapheurType(PdfDocument.ParapheurType.valueOf(parapheurType));
        doc.setParapheurStatut(PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE);
        doc.setSubmittedBy(submittedBy);
        doc.setSubmittedAt(LocalDateTime.now());
        if (destinataire != null && !destinataire.isBlank()) {
            doc.setDestinataire(destinataire);
        }

        return ResponseEntity.ok(repo.save(doc));
    }

    @PostMapping("/{id}/signer")
    public ResponseEntity<PdfDocument> signer(@PathVariable String id,
                                               HttpServletRequest request) throws Exception {
        PdfDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        if (doc.getParapheurStatut() != PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE) {
            return ResponseEntity.badRequest().build();
        }

        AppUser signataire = (AppUser) request.getAttribute("currentUser");
        if (signataire != null) {
            resolveZones(id, signataire.getId());
        }

        doc = finalizer.finalize(id);
        doc.setSignedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());

        // Marquer l'étape courante comme signée
        circuitRepo.findByPdfDocumentIdAndStepOrder(id, doc.getCurrentCircuitStep())
                .ifPresent(etape -> {
                    etape.setStatut(CircuitSignature.StatutEtape.SIGNE);
                    etape.setSignedAt(LocalDateTime.now());
                    circuitRepo.save(etape);
                });

        // Chercher l'étape suivante EN_ATTENTE
        Optional<CircuitSignature> suivante = circuitRepo
                .findFirstByPdfDocumentIdAndStatutOrderByStepOrderAsc(
                        id, CircuitSignature.StatutEtape.EN_ATTENTE);

        if (suivante.isPresent()) {
            // Circuit non terminé : le signataire doit transmettre via son bureau
            CircuitSignature next = suivante.get();

            // Faire du PDF finalisé la nouvelle source (pour que le bureau de transit l'affiche signé)
            // DocumentFinalizationService stocke toujours dans "ged-final-documents" —
            // on met à jour le bucket du PdfDocument pour que les étapes suivantes lisent au bon endroit.
            if (doc.getFinalizedObjectKey() != null) {
                doc.setObjectKey(doc.getFinalizedObjectKey());
                doc.setBucket("ged-final-documents");
                doc.setFinalizedObjectKey(null);
                doc.setStatus("DRAFT");
            }

            // Créer un document de transit dans le bureau du signataire courant
            BureauDocument transit = new BureauDocument();
            transit.setProprietaireId(signataire != null ? signataire.getId() : null);
            transit.setTitre(doc.getTitle());
            transit.setOriginalFileName(doc.getOriginalFileName());
            transit.setBucket(doc.getBucket());        // "ged-final-documents" après la maj ci-dessus
            transit.setObjectKey(doc.getObjectKey()); // clé du PDF signé brûlé
            transit.setPageCount(doc.getPageCount());
            transit.setType(doc.getParapheurType() == PdfDocument.ParapheurType.NOTE_SERVICE
                    ? "NOTE_SERVICE" : "COURRIER");
            if (doc.getDestinataire() != null) transit.setDestinataire(doc.getDestinataire());
            transit.setStatut(BureauDocument.Statut.BROUILLON);
            transit.setCircuitPdfDocumentId(doc.getId());
            transit.setCircuitNextStep(next.getStepOrder());
            bureauRepo.save(transit);

            // Mettre le PdfDocument en attente de transmission (invisible du parapheur)
            doc.setParapheurStatut(PdfDocument.ParapheurStatut.EN_ATTENTE_TRANSMISSION);
            doc.setCurrentSignataireUserId(null);
            doc.setUpdatedAt(LocalDateTime.now());
            PdfDocument saved = repo.save(doc);
            sseService.broadcast("PARAPHEUR_UPDATED", Map.of("action", "EN_ATTENTE_TRANSMISSION", "id", id));
            return ResponseEntity.ok(saved);
        }

        // Dernière étape : clôture du circuit
        if (doc.getParapheurType() == PdfDocument.ParapheurType.NOTE_SERVICE) {
            doc.setParapheurStatut(PdfDocument.ParapheurStatut.PUBLIE);
        } else {
            doc.setParapheurStatut(PdfDocument.ParapheurStatut.SIGNE);
            autoCreateCourrierDepart(doc);
        }
        doc.setCurrentSignataireUserId(null);

        PdfDocument saved = repo.save(doc);
        sseService.broadcast("PARAPHEUR_UPDATED", Map.of("action", "SIGNE", "id", id));

        // Notifier le bureau + clore l'instruction de correction liée
        bureauRepo.findByPdfDocumentId(id).ifPresent(bureau -> {
            bureau.setStatut(BureauDocument.Statut.SIGNE);
            bureau.setSigneAt(LocalDateTime.now());
            bureau.setUpdatedAt(LocalDateTime.now());
            bureauRepo.save(bureau);

            if (bureau.getCorrectionInstructionId() != null) {
                instructionRepo.findById(bureau.getCorrectionInstructionId()).ifPresent(instr -> {
                    if (instr.getStatut() != Instruction.StatutInstruction.CLOTURE) {
                        InstructionMessage sysMsg = new InstructionMessage();
                        sysMsg.setInstruction(instr);
                        sysMsg.setSender("Système");
                        sysMsg.setSelf(false);
                        sysMsg.setText("✅ Document signé — instruction de correction clôturée automatiquement.");
                        sysMsg.setType(InstructionMessage.TypeMessage.SYSTEM);
                        instructionMessageRepo.save(sysMsg);
                        instr.setStatut(Instruction.StatutInstruction.CLOTURE);
                        instructionRepo.save(instr);
                        sseService.broadcast("INSTRUCTION_UPDATED", Map.of(
                            "id", instr.getId(), "statut", "CLOTURE"));
                    }
                });
            }
        });

        // Marquer les documents de transit du circuit comme signés
        bureauRepo.findByCircuitPdfDocumentId(id).forEach(transit -> {
            transit.setStatut(BureauDocument.Statut.SIGNE);
            transit.setSigneAt(LocalDateTime.now());
            transit.setUpdatedAt(LocalDateTime.now());
            bureauRepo.save(transit);
        });

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/rejeter")
    public ResponseEntity<PdfDocument> rejeter(@PathVariable String id,
                                                @RequestBody Map<String, String> body) {
        PdfDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        if (doc.getParapheurStatut() != PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE) {
            return ResponseEntity.badRequest().build();
        }

        doc.setParapheurStatut(PdfDocument.ParapheurStatut.REFUSE);
        doc.setRejectionComment(body.get("comment"));
        doc.setUpdatedAt(LocalDateTime.now());
        PdfDocument savedRejet = repo.save(doc);
        sseService.broadcast("PARAPHEUR_UPDATED", Map.of("action", "RETOURNE", "id", id));
        return ResponseEntity.ok(savedRejet);
    }

    @PostMapping("/{id}/renvoyer")
    public ResponseEntity<PdfDocument> renvoyer(@PathVariable String id,
                                                 @RequestBody Map<String, String> body,
                                                 HttpServletRequest request) {
        PdfDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        if (doc.getParapheurStatut() != PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE) {
            return ResponseEntity.badRequest().build();
        }

        String commentaire = body.get("comment");
        int stepCourant = doc.getCurrentCircuitStep();

        // Marquer l'étape courante comme RENVOYE
        circuitRepo.findByPdfDocumentIdAndStepOrder(id, stepCourant).ifPresent(e -> {
            e.setStatut(CircuitSignature.StatutEtape.RENVOYE);
            circuitRepo.save(e);
        });

        if (stepCourant == 0) {
            // Première étape — renvoyer directement au bureau du propriétaire
            renvoyerAuProprietaire(doc, commentaire, request);
        } else {
            // Renvoi en cascade : revenir à l'étape N-1
            circuitRepo.findByPdfDocumentIdAndStepOrder(id, stepCourant - 1).ifPresent(prec -> {
                prec.setStatut(CircuitSignature.StatutEtape.EN_ATTENTE);
                circuitRepo.save(prec);
                doc.setCurrentSignataireUserId(prec.getSignaireUserId());
                doc.setCurrentCircuitStep(prec.getStepOrder());
            });
            doc.setRejectionComment(commentaire);
            doc.setParapheurStatut(PdfDocument.ParapheurStatut.EN_CORRECTION);
            doc.setUpdatedAt(LocalDateTime.now());
            repo.save(doc);
            sseService.broadcast("PARAPHEUR_UPDATED", Map.of("action", "EN_CORRECTION", "id", id));
        }
        return ResponseEntity.ok(doc);
    }

    // Re-pousser vers l'étape suivante depuis EN_CORRECTION (l'intermédiaire re-valide)
    @PostMapping("/{id}/repousser")
    public ResponseEntity<PdfDocument> repousser(@PathVariable String id) {
        PdfDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        if (doc.getParapheurStatut() != PdfDocument.ParapheurStatut.EN_CORRECTION) {
            return ResponseEntity.badRequest().build();
        }

        circuitRepo.findFirstByPdfDocumentIdAndStatutOrderByStepOrderAsc(
                id, CircuitSignature.StatutEtape.RENVOYE).ifPresent(next -> {
            next.setStatut(CircuitSignature.StatutEtape.EN_ATTENTE);
            circuitRepo.save(next);
            doc.setCurrentSignataireUserId(next.getSignaireUserId());
            doc.setCurrentCircuitStep(next.getStepOrder());
            doc.setParapheurStatut(PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE);
            doc.setUpdatedAt(LocalDateTime.now());
            repo.save(doc);
            sseService.broadcast("PARAPHEUR_UPDATED", Map.of("action", "REPOUSSE", "id", id));
        });
        return ResponseEntity.ok(doc);
    }

    // Consulter les étapes du circuit d'un document
    @GetMapping("/{id}/circuit")
    public List<CircuitSignature> getCircuit(@PathVariable String id) {
        return circuitRepo.findByPdfDocumentIdOrderByStepOrderAsc(id);
    }

    // Cascade complète vers le bureau du propriétaire depuis EN_CORRECTION
    @PostMapping("/{id}/renvoyer-proprietaire")
    public ResponseEntity<PdfDocument> renvoyerProprietaire(@PathVariable String id,
                                                             @RequestBody Map<String, String> body,
                                                             HttpServletRequest request) {
        PdfDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        if (doc.getParapheurStatut() != PdfDocument.ParapheurStatut.EN_CORRECTION) {
            return ResponseEntity.badRequest().build();
        }

        // Annuler toutes les étapes EN_ATTENTE et RENVOYE restantes
        circuitRepo.findByPdfDocumentIdOrderByStepOrderAsc(id).stream()
                .filter(e -> e.getStatut() == CircuitSignature.StatutEtape.EN_ATTENTE
                          || e.getStatut() == CircuitSignature.StatutEtape.RENVOYE)
                .forEach(e -> { e.setStatut(CircuitSignature.StatutEtape.ANNULE); circuitRepo.save(e); });

        renvoyerAuProprietaire(doc, body.get("comment"), request);
        return ResponseEntity.ok(doc);
    }

    // ── POST /api/parapheur/:id/correction ── renvoie + crée instruction avec audio optionnel
    @PostMapping("/{id}/correction")
    public ResponseEntity<PdfDocument> envoyerCorrection(
            @PathVariable String id,
            @RequestParam String comment,
            @RequestParam(required = false) MultipartFile audio,
            @RequestParam(required = false) String highlights,
            HttpServletRequest request) throws Exception {

        PdfDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        if (doc.getParapheurStatut() != PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE) {
            return ResponseEntity.badRequest().build();
        }

        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        String senderName = currentUser != null ? currentUser.getUsername() : "DG";

        // 1. Mettre à jour le PdfDocument
        doc.setParapheurStatut(PdfDocument.ParapheurStatut.RENVOYE);
        doc.setRejectionComment(comment);
        doc.setUpdatedAt(LocalDateTime.now());
        repo.save(doc);

        // 2. Mettre à jour le BureauDocument lié + brûler les surlignages dans le PDF
        Optional<BureauDocument> bureauOpt = bureauRepo.findByPdfDocumentId(id);
        if (bureauOpt.isPresent()) {
            BureauDocument bureau = bureauOpt.get();
            bureau.setStatut(BureauDocument.Statut.RETOURNE);
            bureau.setRetourneAt(LocalDateTime.now());
            bureau.setRenvoyeMotif(comment);
            bureau.setPdfDocumentId(null);
            bureau.setUpdatedAt(LocalDateTime.now());

            if (highlights != null && !highlights.isBlank()) {
                try {
                    byte[] original = minio.downloadBytes(doc.getBucket(), doc.getObjectKey());
                    byte[] annotated = burnHighlights(original, highlights);
                    String newKey = UUID.randomUUID() + "_hl_" + bureau.getOriginalFileName();
                    minio.ensureBucket(bureau.getBucket());
                    minio.uploadBytes(bureau.getBucket(), newKey, annotated, "application/pdf");
                    try { minio.delete(bureau.getBucket(), bureau.getObjectKey()); } catch (Exception ignored) {}
                    bureau.setObjectKey(newKey);
                } catch (Exception e) {
                    // si échec, on conserve le PDF d'origine
                }
            }
            bureauRepo.save(bureau);
        }

        // 3. Upload audio si fourni
        String audioUrl = null;
        String audioName = null;
        if (audio != null && !audio.isEmpty()) {
            minio.ensureBucket(AUDIO_BUCKET);
            String audioKey = UUID.randomUUID() + "_" + audio.getOriginalFilename();
            minio.uploadBytes(AUDIO_BUCKET, audioKey, audio.getBytes(),
                    audio.getContentType() != null ? audio.getContentType() : "audio/mpeg");
            audioUrl  = "/api/parapheur/audio/" + audioKey;
            audioName = audio.getOriginalFilename();
        }

        // 4. Créer l'Instruction
        Instruction instruction = new Instruction();
        instruction.setTitle("Correction/modification demandée sur \"" + doc.getTitle() + "\"");
        instruction.setType("Correction");
        instruction.setUrgence("URGENT");
        instruction.setAgentDisplay("Secrétaire");
        Instruction savedInstruction = instructionRepo.save(instruction);

        // 5. Lier l'instruction au BureauDocument
        bureauOpt.ifPresent(bureau -> {
            bureau.setCorrectionInstructionId(savedInstruction.getId());
            bureauRepo.save(bureau);
        });

        // 6. Créer le message initial
        InstructionMessage msg = new InstructionMessage();
        msg.setInstruction(savedInstruction);
        msg.setSender(senderName);
        msg.setSelf(true);
        msg.setText(comment);
        if (audioUrl != null) {
            msg.setAudioUrl(audioUrl);
            msg.setAttachmentName(audioName);
        }
        if (highlights != null && !highlights.isBlank()) {
            msg.setHighlightsJson(highlights);
        }
        instructionMessageRepo.save(msg);

        // 7. Notifier les clients SSE
        sseService.broadcast("INSTRUCTION_CREATED", Map.of(
            "id", savedInstruction.getId(),
            "title", savedInstruction.getTitle()));
        sseService.broadcast("PARAPHEUR_UPDATED", Map.of("action", "RETOURNE", "id", id));

        return ResponseEntity.ok(doc);
    }

    @GetMapping("/audio/{key}")
    public ResponseEntity<byte[]> getAudio(@PathVariable String key) throws Exception {
        byte[] bytes = minio.downloadBytes(AUDIO_BUCKET, key);
        String ct = "audio/mpeg";
        if (key.endsWith(".webm")) ct = "audio/webm";
        else if (key.endsWith(".ogg")) ct = "audio/ogg";
        else if (key.endsWith(".wav")) ct = "audio/wav";
        else if (key.endsWith(".m4a") || key.endsWith(".mp4")) ct = "audio/mp4";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + key + "\"")
                .body(bytes);
    }

    /** Renvoie le document au bureau du propriétaire original et réinitialise le circuit. */
    private void renvoyerAuProprietaire(PdfDocument doc, String commentaire,
                                         HttpServletRequest request) {
        doc.setParapheurStatut(PdfDocument.ParapheurStatut.RENVOYE);
        doc.setRejectionComment(commentaire);
        doc.setCurrentSignataireUserId(null);
        doc.setUpdatedAt(LocalDateTime.now());
        repo.save(doc);

        bureauRepo.findByPdfDocumentId(doc.getId()).ifPresent(bureau -> {
            bureau.setStatut(BureauDocument.Statut.RETOURNE);
            bureau.setRetourneAt(LocalDateTime.now());
            bureau.setRenvoyeMotif(commentaire);
            bureau.setPdfDocumentId(null);
            bureau.setUpdatedAt(LocalDateTime.now());
            bureauRepo.save(bureau);
        });

        sseService.broadcast("PARAPHEUR_UPDATED", Map.of("action", "RETOURNE", "id", doc.getId()));
    }

    /** Crée les PageAnnotations SIGNATURE_ZONE depuis un JSON de zones pour une étape de circuit. */
    private void creerAnnotationsDepuisZones(String zonesJson, String pdfDocId, String createdBy) {
        if (zonesJson == null || zonesJson.isBlank()) return;
        try {
            List<Map<String, Object>> zones = objectMapper.readValue(zonesJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> z : zones) {
                PageAnnotation a = new PageAnnotation();
                a.setPageId(pdfDocId + "::" + ((Number) z.get("page")).intValue());
                a.setAnnotationType("SIGNATURE_ZONE");
                a.setXPercent(((Number) z.get("x")).doubleValue());
                a.setYPercent(((Number) z.get("y")).doubleValue());
                a.setWidthPercent(((Number) z.get("w")).doubleValue());
                a.setHeightPercent(((Number) z.get("h")).doubleValue());
                a.setCreatedBy(createdBy);
                annotRepo.save(a);
            }
        } catch (Exception ignored) {}
    }

    private void resolveZones(String docId, String signerUserId) {
        List<UserSignatureAsset> assets = assetRepo.findByUserIdAndActiveTrue(signerUserId);
        String sigAssetId = assets.stream()
                .filter(a -> "SIGNATURE".equals(a.getAssetType())).findFirst()
                .map(UserSignatureAsset::getId).orElse(null);
        String stampAssetId = assets.stream()
                .filter(a -> "STAMP".equals(a.getAssetType())).findFirst()
                .map(UserSignatureAsset::getId).orElse(null);

        for (int p = 0; p < 100; p++) {
            String pageId = docId + "::" + p;
            List<PageAnnotation> annots = annotRepo.findByPageId(pageId);
            if (annots.isEmpty()) continue;
            boolean changed = false;
            for (PageAnnotation a : annots) {
                if ("SIGNATURE_ZONE".equals(a.getAnnotationType()) && sigAssetId != null) {
                    a.setAnnotationType("SIGNATURE");
                    a.setSignatureAssetId(sigAssetId);
                    changed = true;
                } else if ("STAMP_ZONE".equals(a.getAnnotationType()) && stampAssetId != null) {
                    a.setAnnotationType("STAMP");
                    a.setSignatureAssetId(stampAssetId);
                    changed = true;
                }
            }
            if (changed) annotRepo.saveAll(annots);
        }
    }

    private void autoCreateCourrierDepart(PdfDocument doc) {
        long count = courrierDepartRepo.count();
        CourrierDepart cd = new CourrierDepart();
        cd.setObjet(doc.getTitle());
        cd.setDestinataire(doc.getDestinataire() != null ? doc.getDestinataire() : "");
        cd.setReference("DG-" + LocalDate.now().getYear() + "-" + String.format("%04d", count + 1));
        cd.setApercu("Document signé via le parapheur électronique.");
        cd.setContenu("");
        cd.setPieceJointe(doc.getFinalizedObjectKey());
        cd.setStatut(CourrierDepart.Statut.SIGNE);
        cd.setDateEnvoi(LocalDate.now());
        courrierDepartRepo.save(cd);
    }

    private byte[] burnHighlights(byte[] pdfBytes, String highlightsJson) throws Exception {
        List<Map<String, Object>> entries;
        try {
            entries = objectMapper.readValue(highlightsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return pdfBytes;
        }
        if (entries.isEmpty()) return pdfBytes;

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            for (Map<String, Object> h : entries) {
                int pageIdx = ((Number) h.get("page")).intValue();
                if (pageIdx < 0 || pageIdx >= pdf.getNumberOfPages()) continue;

                double xPct  = ((Number) h.get("x")).doubleValue();
                double yPct  = ((Number) h.get("y")).doubleValue();
                double wPct  = ((Number) h.get("w")).doubleValue();
                double hPct  = ((Number) h.get("h")).doubleValue();

                PDPage page   = pdf.getPage(pageIdx);
                PDRectangle mb = page.getMediaBox();
                float pw = mb.getWidth();
                float ph = mb.getHeight();

                float rx = (float)(xPct / 100.0 * pw);
                float rw = (float)(wPct / 100.0 * pw);
                float rh = (float)(hPct / 100.0 * ph);
                float ry = (float)((1.0 - (yPct + hPct) / 100.0) * ph);

                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(0.35f);

                try (PDPageContentStream cs = new PDPageContentStream(
                        pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setGraphicsStateParameters(gs);
                    cs.setNonStrokingColor(1.0f, 0.95f, 0.0f);
                    cs.addRect(rx, ry, rw, rh);
                    cs.fill();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pdf.save(baos);
            return baos.toByteArray();
        }
    }
}
