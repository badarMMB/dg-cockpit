package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.BureauDocument;
import com.dgcockpit.entity.PageAnnotation;
import com.dgcockpit.entity.PdfDocument;
import com.dgcockpit.repository.BureauDocumentRepository;
import com.dgcockpit.repository.PageAnnotationRepository;
import com.dgcockpit.repository.PdfDocumentRepository;
import com.dgcockpit.service.DocumentFinalizationService;
import com.dgcockpit.service.MinioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
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
    private final MinioService minio;
    private final DocumentFinalizationService finalizer;

    public BureauController(BureauDocumentRepository bureauRepo,
                            PdfDocumentRepository pdfRepo,
                            PageAnnotationRepository annotRepo,
                            MinioService minio,
                            DocumentFinalizationService finalizer) {
        this.bureauRepo = bureauRepo;
        this.pdfRepo = pdfRepo;
        this.annotRepo = annotRepo;
        this.minio = minio;
        this.finalizer = finalizer;
    }

    // ── POST /api/bureau/documents ── upload PDF (navigateur OU imprimante virtuelle)
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

    // ── GET /api/bureau/documents ── liste des brouillons de la Secrétaire connectée
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

        @SuppressWarnings("unchecked")
        Map<String, Object> sig = (Map<String, Object>) body.get("signatureZone");
        if (sig != null) {
            doc.setSignatureZonePage(toInt(sig.get("page")));
            doc.setSignatureZoneX(toDouble(sig.get("x")));
            doc.setSignatureZoneY(toDouble(sig.get("y")));
            doc.setSignatureZoneW(toDouble(sig.get("w")));
            doc.setSignatureZoneH(toDouble(sig.get("h")));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> stamp = (Map<String, Object>) body.get("stampZone");
        if (stamp != null) {
            doc.setStampZonePage(toInt(stamp.get("page")));
            doc.setStampZoneX(toDouble(stamp.get("x")));
            doc.setStampZoneY(toDouble(stamp.get("y")));
            doc.setStampZoneW(toDouble(stamp.get("w")));
            doc.setStampZoneH(toDouble(stamp.get("h")));
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
        if (doc.getSignatureZonePage() == null) {
            return ResponseEntity.badRequest().build(); // zone signature obligatoire
        }

        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        String submittedBy = currentUser != null ? currentUser.getUsername() : "secretaire";

        // Déterminer le type parapheur
        PdfDocument.ParapheurType parapheurType = "NOTE_SERVICE".equals(doc.getType())
                ? PdfDocument.ParapheurType.NOTE_SERVICE
                : PdfDocument.ParapheurType.COURRIER;

        // Créer le PdfDocument
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

        // Créer annotation SIGNATURE_ZONE
        PageAnnotation sigAnnot = new PageAnnotation();
        sigAnnot.setPageId(savedPdf.getId() + "::" + doc.getSignatureZonePage());
        sigAnnot.setAnnotationType("SIGNATURE_ZONE");
        sigAnnot.setXPercent(doc.getSignatureZoneX());
        sigAnnot.setYPercent(doc.getSignatureZoneY());
        sigAnnot.setWidthPercent(doc.getSignatureZoneW());
        sigAnnot.setHeightPercent(doc.getSignatureZoneH());
        sigAnnot.setCreatedBy(submittedBy);
        annotRepo.save(sigAnnot);

        // Créer annotation STAMP_ZONE si présente
        if (doc.getStampZonePage() != null) {
            PageAnnotation stampAnnot = new PageAnnotation();
            stampAnnot.setPageId(savedPdf.getId() + "::" + doc.getStampZonePage());
            stampAnnot.setAnnotationType("STAMP_ZONE");
            stampAnnot.setXPercent(doc.getStampZoneX());
            stampAnnot.setYPercent(doc.getStampZoneY());
            stampAnnot.setWidthPercent(doc.getStampZoneW());
            stampAnnot.setHeightPercent(doc.getStampZoneH());
            stampAnnot.setCreatedBy(submittedBy);
            annotRepo.save(stampAnnot);
        }

        // Marquer le BureauDocument comme soumis
        doc.setStatut(BureauDocument.Statut.SOUMIS);
        doc.setPdfDocumentId(savedPdf.getId());
        doc.setUpdatedAt(LocalDateTime.now());
        bureauRepo.save(doc);

        Map<String, Object> result = new HashMap<>();
        result.put("bureauDocumentId", doc.getId());
        result.put("pdfDocumentId", savedPdf.getId());
        result.put("statut", "SOUMIS");
        return ResponseEntity.ok(result);
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

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
        m.put("hasSignatureZone", d.getSignatureZonePage() != null);
        m.put("hasStampZone", d.getStampZonePage() != null);
        if (d.getSignatureZonePage() != null) {
            Map<String, Object> sig = new HashMap<>();
            sig.put("page", d.getSignatureZonePage());
            sig.put("x", d.getSignatureZoneX());
            sig.put("y", d.getSignatureZoneY());
            sig.put("w", d.getSignatureZoneW());
            sig.put("h", d.getSignatureZoneH());
            m.put("signatureZone", sig);
        }
        if (d.getStampZonePage() != null) {
            Map<String, Object> stamp = new HashMap<>();
            stamp.put("page", d.getStampZonePage());
            stamp.put("x", d.getStampZoneX());
            stamp.put("y", d.getStampZoneY());
            stamp.put("w", d.getStampZoneW());
            stamp.put("h", d.getStampZoneH());
            m.put("stampZone", stamp);
        }
        return m;
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
