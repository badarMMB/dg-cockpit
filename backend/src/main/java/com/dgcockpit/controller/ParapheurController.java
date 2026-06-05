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
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final com.dgcockpit.service.AuthorizationService authorizationService;
    private final com.dgcockpit.service.WopiTokenService wopiTokenService;
    private final com.dgcockpit.service.DocxSignatureService docxSignatureService;
    private final com.dgcockpit.service.CollaboraConvertService collaboraConvertService;
    private final com.dgcockpit.service.WorkflowEngineService workflowEngine;

    @org.springframework.beans.factory.annotation.Value("${collabora.public-url:http://localhost:9980}")
    private String collaboraPublicUrl;

    @org.springframework.beans.factory.annotation.Value("${collabora.wopi.host:http://localhost:8080}")
    private String wopiHost;

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
                               ObjectMapper objectMapper,
                               com.dgcockpit.service.AuthorizationService authorizationService,
                               com.dgcockpit.service.WopiTokenService wopiTokenService,
                               com.dgcockpit.service.DocxSignatureService docxSignatureService,
                               com.dgcockpit.service.CollaboraConvertService collaboraConvertService,
                               com.dgcockpit.service.WorkflowEngineService workflowEngine) {
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
        this.authorizationService = authorizationService;
        this.wopiTokenService = wopiTokenService;
        this.docxSignatureService    = docxSignatureService;
        this.collaboraConvertService = collaboraConvertService;
        this.workflowEngine          = workflowEngine;
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
    @PreAuthorize("hasAuthority('CAN_SIGN')")
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

        // Filet de sécurité : si le PdfDocument pointe encore vers un .docx (données
        // antérieures au flux hybride), le convertir en PDF avant le brûlage PDFBox.
        if (doc.getOriginalFileName() != null
                && doc.getOriginalFileName().toLowerCase().endsWith(".docx")) {
            try {
                byte[] docxBytes = minio.downloadBytes(doc.getBucket(), doc.getObjectKey());
                byte[] pdfBytes  = collaboraConvertService.docxToPdf(docxBytes, doc.getOriginalFileName());
                String pdfKey = java.util.UUID.randomUUID() + "_"
                    + doc.getOriginalFileName().replaceAll("\\.docx$", ".pdf");
                minio.uploadBytes("ged-documents", pdfKey, pdfBytes, "application/pdf");
                int pages = finalizer.getPageCount("ged-documents", pdfKey);
                doc.setBucket("ged-documents");
                doc.setObjectKey(pdfKey);
                doc.setPageCount(pages);
                doc.setUpdatedAt(java.time.LocalDateTime.now());
                doc = repo.save(doc);
            } catch (Exception e) {
                return ResponseEntity.status(500).build();
            }
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
            transit.setBucket(doc.getBucket());
            transit.setObjectKey(doc.getObjectKey());
            transit.setPageCount(doc.getPageCount());
            transit.setType(doc.getParapheurType() == PdfDocument.ParapheurType.NOTE_SERVICE
                    ? "NOTE_SERVICE" : "COURRIER");
            if (doc.getDestinataire() != null) transit.setDestinataire(doc.getDestinataire());
            transit.setStatut(BureauDocument.Statut.BROUILLON);
            transit.setCircuitPdfDocumentId(doc.getId());
            transit.setCircuitNextStep(next.getStepOrder());
            // Pré-remplir les zones du prochain signataire (zones nominatives)
            // afin que le bureau de transit arrive déjà zoné pour ce signataire.
            if (next.getSignatureZonesJson() != null && !next.getSignatureZonesJson().isBlank()) {
                transit.setSignatureZonesJson(next.getSignatureZonesJson());
            }
            bureauRepo.save(transit);

            // Mettre le PdfDocument en attente de transmission (invisible du parapheur)
            doc.setParapheurStatut(PdfDocument.ParapheurStatut.EN_ATTENTE_TRANSMISSION);
            doc.setCurrentSignataireUserId(null);
            doc.setUpdatedAt(LocalDateTime.now());
            PdfDocument saved = repo.save(doc);

            // Message système dans l'instruction liée (étape du circuit signée)
            final int stepSigne = saved.getCurrentCircuitStep();
            final AppUser sigFinal = signataire;
            bureauRepo.findByPdfDocumentId(id).ifPresent(srcBureau -> {
                if (srcBureau.getSourceInstructionId() != null) {
                    String nom = sigFinal != null ? sigFinal.getNomComplet() : "Signataire";
                    ajouterMessageSysteme(srcBureau.getSourceInstructionId(),
                        "✍️ Étape " + (stepSigne + 1) + " signée par " + nom);
                }
            });

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

        // Invalider tous les tokens WOPI du bureau source (plus d'édition possible)
        bureauRepo.findByPdfDocumentId(id).ifPresent(bureau ->
            wopiTokenService.invalidateForDocument(bureau.getId()));

        // Notifier le bureau + clôturer l'instruction liée (si DOCUMENTAIRE)
        final PdfDocument docFinal = doc;
        bureauRepo.findByPdfDocumentId(id).ifPresent(bureau -> {
            bureau.setStatut(BureauDocument.Statut.SIGNE);
            bureau.setSigneAt(LocalDateTime.now());
            bureau.setUpdatedAt(LocalDateTime.now());
            bureauRepo.save(bureau);

            if (bureau.getSourceInstructionId() != null) {
                instructionRepo.findById(bureau.getSourceInstructionId()).ifPresent(instr -> {
                    if (instr.getStatut() != Instruction.StatutInstruction.CLOTURE) {
                        InstructionMessage sysMsg = new InstructionMessage();
                        sysMsg.setInstruction(instr);
                        sysMsg.setSender("Système");
                        sysMsg.setSelf(false);
                        sysMsg.setSystemMessage(true);
                        sysMsg.setText("✅ Document \"" + docFinal.getTitle()
                            + "\" finalisé → instruction clôturée automatiquement.");
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

        // Avancer le workflow si ce PdfDocument est piloté par un WorkflowInstance
        if (saved.getWorkflowInstanceId() != null) {
            try {
                workflowEngine.moveToNextStep(saved.getWorkflowInstanceId(),
                        signataire != null ? signataire.getId() : "system");
            } catch (Exception ignored) {}
        }

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

            // Injecter le commentaire dans le fil de l'instruction liée
            AppUser signeur = (AppUser) request.getAttribute("currentUser");
            String signerNom = signeur != null ? signeur.getNomComplet() : "Signataire";
            bureauRepo.findByPdfDocumentId(id).ifPresent(bureau -> {
                String srcId = bureau.getSourceInstructionId();
                if (srcId != null && !srcId.isBlank()) {
                    ajouterMessageSysteme(srcId,
                        "↩️ Renvoyé pour correction par " + signerNom
                        + (commentaire != null && !commentaire.isBlank() ? " : " + commentaire : ""));
                }
            });

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

        // 4. Reporter dans l'instruction liée OU créer une instruction LIBRE (rétrocompat)
        String linkedInstructionId = bureauOpt
                .map(BureauDocument::getSourceInstructionId)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);

        if (linkedInstructionId != null) {
            // Instruction DOCUMENTAIRE existante → ajouter les messages dans le fil
            final String audioUrlFinal = audioUrl;
            final String audioNameFinal = audioName;
            instructionRepo.findById(linkedInstructionId).ifPresent(instr -> {
                if (instr.getStatut() != Instruction.StatutInstruction.CLOTURE) {
                    InstructionMessage sysMsg = new InstructionMessage();
                    sysMsg.setInstruction(instr);
                    sysMsg.setSender("Système");
                    sysMsg.setSelf(false);
                    sysMsg.setSystemMessage(true);
                    sysMsg.setText("↩️ Renvoyé pour correction : " + comment);
                    if (highlights != null && !highlights.isBlank()) sysMsg.setHighlightsJson(highlights);
                    instructionMessageRepo.save(sysMsg);

                    if (audioUrlFinal != null) {
                        InstructionMessage audioMsg = new InstructionMessage();
                        audioMsg.setInstruction(instr);
                        audioMsg.setSender(senderName);
                        audioMsg.setSelf(false);
                        audioMsg.setAudioUrl(audioUrlFinal);
                        audioMsg.setAttachmentName(audioNameFinal);
                        instructionMessageRepo.save(audioMsg);
                    }

                    instr.setStatut(Instruction.StatutInstruction.EN_COURS);
                    instructionRepo.save(instr);
                    sseService.broadcast("INSTRUCTION_UPDATED", Map.of(
                        "id", linkedInstructionId, "statut", "EN_COURS"));
                }
            });
        } else {
            // Pas d'instruction liée → créer une nouvelle instruction LIBRE (rétrocompat)
            Instruction instruction = new Instruction();
            instruction.setTitle("Correction/modification demandée sur \"" + doc.getTitle() + "\"");
            instruction.setType("Correction");
            instruction.setUrgence("URGENT");
            instruction.setAgentDisplay("Secrétaire");
            if (currentUser != null) instruction.setCreatedById(currentUser.getId());
            Instruction savedInstruction = instructionRepo.save(instruction);

            bureauOpt.ifPresent(bureau -> {
                bureau.setSourceInstructionId(savedInstruction.getId());
                bureauRepo.save(bureau);
            });

            InstructionMessage msg = new InstructionMessage();
            msg.setInstruction(savedInstruction);
            msg.setSender(senderName);
            msg.setSelf(true);
            msg.setText(comment);
            if (audioUrl != null) {
                msg.setAudioUrl(audioUrl);
                msg.setAttachmentName(audioName);
            }
            if (highlights != null && !highlights.isBlank()) msg.setHighlightsJson(highlights);
            instructionMessageRepo.save(msg);

            sseService.broadcast("INSTRUCTION_CREATED", Map.of(
                "id", savedInstruction.getId(), "title", savedInstruction.getTitle()));
        }

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

            if (bureau.getSourceInstructionId() != null) {
                ajouterMessageSysteme(bureau.getSourceInstructionId(),
                    "↩️ Renvoyé pour correction : " + commentaire);
            }
        });

        sseService.broadcast("PARAPHEUR_UPDATED", Map.of("action", "RETOURNE", "id", doc.getId()));
    }

    // ── GET /api/parapheur/{pdfDocId}/source-docx ── retrouve le .docx source d'un PdfDocument
    // Permet au pdf-viewer d'ouvrir Collabora (édition DG) à partir de l'id du PdfDocument signé.
    @GetMapping("/{pdfDocId}/source-docx")
    public ResponseEntity<Map<String, Object>> sourceDocx(@PathVariable String pdfDocId) {
        BureauDocument bureau = bureauRepo.findByPdfDocumentId(pdfDocId).orElse(null);
        boolean isDocx = bureau != null && bureau.getOriginalFileName() != null
            && bureau.getOriginalFileName().toLowerCase().endsWith(".docx");
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("bureauDocumentId", bureau != null ? bureau.getId() : null);
        body.put("isDocx", isDocx);
        return ResponseEntity.ok(body);
    }

    // ── GET /api/parapheur/{id}/wopi-session ── amorce une session Collabora au Parapheur
    // id = bureauDocument.id (le document source .docx en attente de signature)
    @GetMapping("/{id}/wopi-session")
    public ResponseEntity<Map<String, Object>> openWopiSession(
            @PathVariable String id,
            HttpServletRequest request) {

        AppUser current = (AppUser) request.getAttribute("currentUser");
        com.dgcockpit.entity.BureauDocument doc = bureauRepo.findById(id)
            .orElseThrow(() -> new com.dgcockpit.exception.AccesRefuseException("Document introuvable"));

        boolean canWrite = authorizationService.canEditParapheurDocument(current, doc);
        com.dgcockpit.entity.WopiToken token = wopiTokenService.issue(current.getId(), doc.getId(), canWrite);

        String wopiSrc = wopiHost + "/api/wopi/files/" + doc.getId();
        String collaboraUrl = collaboraPublicUrl
            + "/browser/dist/cool.html"
            + "?WOPISrc=" + java.net.URLEncoder.encode(wopiSrc, java.nio.charset.StandardCharsets.UTF_8)
            + "&closebutton=true&revisionhistory=false";

        return ResponseEntity.ok(Map.of(
            "collaboraUrl",    collaboraUrl,
            "accessToken",     token.getToken(),
            "accessTokenTtl",  token.getExpiresAt().toEpochMilli(),
            "canWrite",        canWrite
        ));
    }

    /**
     * Réajustement de zone par le signataire avant de signer.
     * Remplace les PageAnnotations SIGNATURE_ZONE existantes de la page concernée
     * par la nouvelle position fournie.
     * Body : { page, x, y, w, h }
     */
    @PutMapping("/{id}/adjust-zone")
    public ResponseEntity<Void> adjustZone(@PathVariable String id,
                                            @RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        PdfDocument doc = repo.findById(id).orElse(null);
        if (doc == null) return ResponseEntity.notFound().build();
        if (doc.getParapheurStatut() != PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE) {
            return ResponseEntity.badRequest().build();
        }
        AppUser current = (AppUser) request.getAttribute("currentUser");
        if (current == null || !current.getId().equals(doc.getCurrentSignataireUserId())) {
            return ResponseEntity.status(403).build();
        }

        int page = ((Number) body.get("page")).intValue();
        String pageId = id + "::" + page;

        // Remplacer les SIGNATURE_ZONE de cette page
        List<PageAnnotation> existing = annotRepo.findByPageId(pageId).stream()
            .filter(a -> "SIGNATURE_ZONE".equals(a.getAnnotationType()))
            .toList();
        annotRepo.deleteAll(existing);

        PageAnnotation a = new PageAnnotation();
        a.setPageId(pageId);
        a.setAnnotationType("SIGNATURE_ZONE");
        a.setXPercent(((Number) body.get("x")).doubleValue());
        a.setYPercent(((Number) body.get("y")).doubleValue());
        a.setWidthPercent(((Number) body.get("w")).doubleValue());
        a.setHeightPercent(((Number) body.get("h")).doubleValue());
        a.setCreatedBy(current.getUsername());
        annotRepo.save(a);

        return ResponseEntity.noContent().build();
    }

    private void ajouterMessageSysteme(String instructionId, String texte) {
        instructionRepo.findById(instructionId).ifPresent(instr -> {
            if (instr.getStatut() != Instruction.StatutInstruction.CLOTURE) {
                InstructionMessage msg = new InstructionMessage();
                msg.setInstruction(instr);
                msg.setSender("Système");
                msg.setSelf(false);
                msg.setSystemMessage(true);
                msg.setText(texte);
                instructionMessageRepo.save(msg);
                sseService.broadcast("INSTRUCTION_UPDATED", Map.of(
                    "id", instructionId, "statut", instr.getStatut().name()));
            }
        });
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
