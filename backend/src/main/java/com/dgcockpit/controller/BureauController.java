package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.BureauDocument;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.entity.PageAnnotation;
import com.dgcockpit.entity.PdfDocument;
import com.dgcockpit.repository.BureauDocumentRepository;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import com.dgcockpit.repository.PageAnnotationRepository;
import com.dgcockpit.repository.PdfDocumentRepository;
import com.dgcockpit.service.DocumentFinalizationService;
import com.dgcockpit.service.MinioService;
import com.dgcockpit.sse.SseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bureau")
@CrossOrigin(origins = "*")
public class BureauController {

    private static final String BUCKET = "ged-bureau-documents";

    private final BureauDocumentRepository bureauRepo;
    private final PdfDocumentRepository pdfRepo;
    private final PageAnnotationRepository annotRepo;
    private final InstructionRepository instructionRepo;
    private final InstructionMessageRepository instructionMessageRepo;
    private final MinioService minio;
    private final DocumentFinalizationService finalizer;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    public BureauController(BureauDocumentRepository bureauRepo,
                            PdfDocumentRepository pdfRepo,
                            PageAnnotationRepository annotRepo,
                            InstructionRepository instructionRepo,
                            InstructionMessageRepository instructionMessageRepo,
                            MinioService minio,
                            DocumentFinalizationService finalizer,
                            SseService sseService,
                            ObjectMapper objectMapper) {
        this.bureauRepo = bureauRepo;
        this.pdfRepo = pdfRepo;
        this.annotRepo = annotRepo;
        this.instructionRepo = instructionRepo;
        this.instructionMessageRepo = instructionMessageRepo;
        this.minio = minio;
        this.finalizer = finalizer;
        this.sseService = sseService;
        this.objectMapper = objectMapper;
    }

    // ── POST /api/bureau/documents ── upload PDF
    @PostMapping("/documents")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "COURRIER") String type,
            @RequestParam(required = false) String titre,
            @RequestParam(required = false) String destinataire,
            HttpServletRequest request) throws Exception {

        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        String secretaireId = currentUser != null ? currentUser.getId() : "anonymous";

        minio.ensureBucket(BUCKET);
        String objectKey = UUID.randomUUID() + "_" + file.getOriginalFilename();
        minio.uploadBytes(BUCKET, objectKey, file.getBytes(), "application/pdf");

        int pageCount = finalizer.getPageCount(BUCKET, objectKey);

        BureauDocument doc = new BureauDocument();
        doc.setSecretaireId(secretaireId);
        doc.setTitre(titre != null && !titre.isBlank() ? titre : file.getOriginalFilename());
        doc.setType(type);
        doc.setDestinataire(destinataire);
        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setBucket(BUCKET);
        doc.setObjectKey(objectKey);
        doc.setPageCount(pageCount);

        return ResponseEntity.ok(toDto(bureauRepo.save(doc)));
    }

    // ── GET /api/bureau/documents ── liste des documents de la Secrétaire connectée
    @GetMapping("/documents")
    public List<Map<String, Object>> list(HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        String secretaireId = currentUser != null ? currentUser.getId() : "anonymous";
        return bureauRepo.findBySecretaireIdOrderByCreatedAtDesc(secretaireId)
                .stream().map(this::toDto).toList();
    }

    // ── GET /api/bureau/documents/:id/page/:n ── rendu d'une page en PNG
    @GetMapping("/documents/{id}/page/{pageIndex}")
    public ResponseEntity<byte[]> renderPage(@PathVariable String id,
                                             @PathVariable int pageIndex) throws Exception {
        BureauDocument doc = bureauRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BureauDocument introuvable: " + id));
        byte[] png = finalizer.renderPageFromStorage(doc.getBucket(), doc.getObjectKey(), pageIndex);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(png);
    }

    // ── GET /api/bureau/documents/:id/stream ── diffuse le PDF source pour aperçu inline
    @GetMapping("/documents/{id}/stream")
    public ResponseEntity<byte[]> streamPdf(@PathVariable String id) throws Exception {
        BureauDocument doc = bureauRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BureauDocument introuvable: " + id));
        byte[] bytes = minio.downloadBytes(doc.getBucket(), doc.getObjectKey());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getOriginalFileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(bytes);
    }

    // ── PUT /api/bureau/documents/:id/pdf ── remplace le PDF source
    @PutMapping("/documents/{id}/pdf")
    public ResponseEntity<Map<String, Object>> replacePdf(
            @PathVariable String id,
            @RequestParam MultipartFile file,
            HttpServletRequest request) throws Exception {

        BureauDocument doc = bureauRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BureauDocument introuvable: " + id));

        if (doc.getStatut() == BureauDocument.Statut.SOUMIS) {
            return ResponseEntity.status(403).build();
        }

        try { minio.delete(doc.getBucket(), doc.getObjectKey()); } catch (Exception ignored) {}

        String newKey = UUID.randomUUID() + "_" + file.getOriginalFilename();
        minio.uploadBytes(BUCKET, newKey, file.getBytes(), "application/pdf");
        int newPageCount = finalizer.getPageCount(BUCKET, newKey);

        doc.setObjectKey(newKey);
        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setPageCount(newPageCount);
        doc.setUpdatedAt(LocalDateTime.now());

        return ResponseEntity.ok(toDto(bureauRepo.save(doc)));
    }

    // ── DELETE /api/bureau/documents/:id
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) {
        BureauDocument doc = bureauRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BureauDocument introuvable: " + id));
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        String secretaireId = currentUser != null ? currentUser.getId() : "";
        if (!doc.getSecretaireId().equals(secretaireId)) return ResponseEntity.status(403).build();
        try { minio.delete(doc.getBucket(), doc.getObjectKey()); } catch (Exception ignored) {}
        bureauRepo.delete(doc);
        return ResponseEntity.noContent().build();
    }

    // ── POST /api/bureau/documents/:id/zones ── sauvegarder positions signature + tampon
    @PostMapping("/documents/{id}/zones")
    public ResponseEntity<Map<String, Object>> saveZones(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        BureauDocument doc = bureauRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BureauDocument introuvable: " + id));

        try {
            Object sigZones = body.get("signatureZones");
            if (sigZones != null) {
                doc.setSignatureZonesJson(objectMapper.writeValueAsString(sigZones));
            }
            Object stZones = body.get("stampZones");
            if (stZones != null) {
                doc.setStampZonesJson(objectMapper.writeValueAsString(stZones));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        doc.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(toDto(bureauRepo.save(doc)));
    }

    // ── POST /api/bureau/documents/:id/soumettre ── crée PdfDocument + envoie au parapheur
    @PostMapping("/documents/{id}/soumettre")
    public ResponseEntity<Map<String, Object>> soumettre(
            @PathVariable String id,
            HttpServletRequest request) {

        BureauDocument doc = bureauRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BureauDocument introuvable: " + id));

        if (doc.getStatut() == BureauDocument.Statut.SOUMIS) {
            return ResponseEntity.badRequest().build();
        }

        final boolean isResoumission = doc.getStatut() == BureauDocument.Statut.RETOURNE;
        if (isResoumission) {
            doc.setRenvoyeMotif(null);
            doc.setHighlightsJson(null);
        }

        List<Map<String, Object>> sigZones = parseZones(doc.getSignatureZonesJson());
        if (sigZones.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        String submittedBy = currentUser != null ? currentUser.getUsername() : "secretaire";

        PdfDocument.ParapheurType parapheurType = "NOTE_SERVICE".equals(doc.getType())
                ? PdfDocument.ParapheurType.NOTE_SERVICE
                : PdfDocument.ParapheurType.COURRIER;

        PdfDocument pdf = new PdfDocument();
        pdf.setTitle(doc.getTitre());
        pdf.setOriginalFileName(doc.getOriginalFileName());
        pdf.setBucket(doc.getBucket());
        pdf.setObjectKey(doc.getObjectKey());
        pdf.setPageCount(doc.getPageCount() != null ? doc.getPageCount() : 1);
        pdf.setStatus("DRAFT");
        pdf.setParapheurType(parapheurType);
        pdf.setParapheurStatut(PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE);
        pdf.setSubmittedBy(submittedBy);
        pdf.setSubmittedAt(LocalDateTime.now());
        if (doc.getDestinataire() != null) pdf.setDestinataire(doc.getDestinataire());

        PdfDocument savedPdf = pdfRepo.save(pdf);

        for (Map<String, Object> z : sigZones) {
            PageAnnotation a = new PageAnnotation();
            a.setPageId(savedPdf.getId() + "::" + toInt(z.get("page")));
            a.setAnnotationType("SIGNATURE_ZONE");
            a.setXPercent(toDouble(z.get("x")));
            a.setYPercent(toDouble(z.get("y")));
            a.setWidthPercent(toDouble(z.get("w")));
            a.setHeightPercent(toDouble(z.get("h")));
            a.setCreatedBy(submittedBy);
            annotRepo.save(a);
        }

        for (Map<String, Object> z : parseZones(doc.getStampZonesJson())) {
            PageAnnotation a = new PageAnnotation();
            a.setPageId(savedPdf.getId() + "::" + toInt(z.get("page")));
            a.setAnnotationType("STAMP_ZONE");
            a.setXPercent(toDouble(z.get("x")));
            a.setYPercent(toDouble(z.get("y")));
            a.setWidthPercent(toDouble(z.get("w")));
            a.setHeightPercent(toDouble(z.get("h")));
            a.setCreatedBy(submittedBy);
            annotRepo.save(a);
        }

        if (doc.getReference() == null) {
            int year = LocalDateTime.now().getYear();
            int nextNum = bureauRepo.findMaxReferenceNumberForYear(year) + 1;
            doc.setReferenceNumber(nextNum);
            doc.setReferenceYear(year);
            doc.setReference(String.format("N°%03d/DG/%d", nextNum, year));
        }

        doc.setStatut(BureauDocument.Statut.SOUMIS);
        doc.setSoumisAt(LocalDateTime.now());
        doc.setPdfDocumentId(savedPdf.getId());
        doc.setUpdatedAt(LocalDateTime.now());
        bureauRepo.save(doc);

        // Lors d'une re-soumission, réactiver l'instruction de correction liée
        if (isResoumission && doc.getCorrectionInstructionId() != null) {
            instructionRepo.findById(doc.getCorrectionInstructionId()).ifPresent(instr -> {
                if (instr.getStatut() != Instruction.StatutInstruction.CLOTURE) {
                    InstructionMessage sysMsg = new InstructionMessage();
                    sysMsg.setInstruction(instr);
                    sysMsg.setSender("Système");
                    sysMsg.setSelf(false);
                    sysMsg.setText("📤 Document re-soumis au parapheur — en attente de signature DG.");
                    sysMsg.setType(InstructionMessage.TypeMessage.SYSTEM);
                    instructionMessageRepo.save(sysMsg);
                    instr.setStatut(Instruction.StatutInstruction.EN_COURS);
                    instructionRepo.save(instr);
                    sseService.broadcast("INSTRUCTION_UPDATED", Map.of(
                        "id", instr.getId(), "statut", "EN_COURS"));
                }
            });
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bureauDocumentId", doc.getId());
        result.put("pdfDocumentId", savedPdf.getId());
        result.put("statut", "SOUMIS");
        result.put("reference", doc.getReference());
        return ResponseEntity.ok(result);
    }

    // ── DTO ───────────────────────────────────────────────────────────────

    private Map<String, Object> toDto(BureauDocument d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("titre", d.getTitre());
        m.put("type", d.getType());
        m.put("destinataire", d.getDestinataire());
        m.put("originalFileName", d.getOriginalFileName());
        m.put("pageCount", d.getPageCount());
        m.put("statut", d.getStatut().name());
        m.put("pdfDocumentId", d.getPdfDocumentId());
        m.put("createdAt", d.getCreatedAt().toString());

        List<Map<String, Object>> sigZones = parseZones(d.getSignatureZonesJson());
        List<Map<String, Object>> stZones  = parseZones(d.getStampZonesJson());
        m.put("signatureZones", sigZones);
        m.put("stampZones", stZones);
        m.put("hasSignatureZone", !sigZones.isEmpty());
        m.put("hasStampZone", !stZones.isEmpty());
        m.put("renvoyeMotif", d.getRenvoyeMotif());
        m.put("highlights", parseZones(d.getHighlightsJson()));
        m.put("reference", d.getReference());
        m.put("soumisAt",   d.getSoumisAt()   != null ? d.getSoumisAt().toString()   : null);
        m.put("signeAt",    d.getSigneAt()    != null ? d.getSigneAt().toString()    : null);
        m.put("retourneAt", d.getRetourneAt() != null ? d.getRetourneAt().toString() : null);
        m.put("livreAt",    d.getLivreAt()    != null ? d.getLivreAt().toString()    : null);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseZones(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return 0;
    }
}
