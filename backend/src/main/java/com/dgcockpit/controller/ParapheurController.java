package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.CourrierDepart;
import com.dgcockpit.entity.PageAnnotation;
import com.dgcockpit.entity.PdfDocument;
import com.dgcockpit.entity.UserSignatureAsset;
import com.dgcockpit.repository.CourrierDepartRepository;
import com.dgcockpit.repository.PageAnnotationRepository;
import com.dgcockpit.repository.PdfDocumentRepository;
import com.dgcockpit.repository.UserSignatureAssetRepository;
import com.dgcockpit.service.DocumentFinalizationService;
import com.dgcockpit.service.MinioService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    public ParapheurController(PdfDocumentRepository repo,
                               DocumentFinalizationService finalizer,
                               MinioService minio,
                               CourrierDepartRepository courrierDepartRepo,
                               PageAnnotationRepository annotRepo,
                               UserSignatureAssetRepository assetRepo) {
        this.repo = repo;
        this.finalizer = finalizer;
        this.minio = minio;
        this.courrierDepartRepo = courrierDepartRepo;
        this.annotRepo = annotRepo;
        this.assetRepo = assetRepo;
    }

    @GetMapping
    public List<PdfDocument> pending() {
        return repo.findByParapheurStatutOrderBySubmittedAtDesc(
                PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE);
    }

    @GetMapping("/historique")
    public List<PdfDocument> historique() {
        return repo.findHistorique(List.of(
                PdfDocument.ParapheurStatut.SIGNE,
                PdfDocument.ParapheurStatut.REFUSE,
                PdfDocument.ParapheurStatut.PUBLIE));
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

        // Résoudre les zones SIGNATURE_ZONE / STAMP_ZONE avec les assets réels du DG
        AppUser signer = (AppUser) request.getAttribute("currentUser");
        if (signer != null) {
            resolveZones(id, signer.getId());
        }

        // Bake annotations into final PDF
        doc = finalizer.finalize(id);

        doc.setSignedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());

        if (doc.getParapheurType() == PdfDocument.ParapheurType.NOTE_SERVICE) {
            doc.setParapheurStatut(PdfDocument.ParapheurStatut.PUBLIE);
        } else {
            doc.setParapheurStatut(PdfDocument.ParapheurStatut.SIGNE);
            autoCreateCourrierDepart(doc);
        }

        return ResponseEntity.ok(repo.save(doc));
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
        return ResponseEntity.ok(repo.save(doc));
    }

    private void resolveZones(String docId, String signerUserId) {
        List<UserSignatureAsset> assets = assetRepo.findByUserIdAndActiveTrue(signerUserId);
        String sigAssetId = assets.stream()
                .filter(a -> "SIGNATURE".equals(a.getAssetType())).findFirst()
                .map(UserSignatureAsset::getId).orElse(null);
        String stampAssetId = assets.stream()
                .filter(a -> "STAMP".equals(a.getAssetType())).findFirst()
                .map(UserSignatureAsset::getId).orElse(null);

        // Parcourir toutes les pages du document
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
}
