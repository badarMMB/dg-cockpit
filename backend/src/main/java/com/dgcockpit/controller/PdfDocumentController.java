package com.dgcockpit.controller;

import com.dgcockpit.entity.PdfDocument;
import com.dgcockpit.repository.PdfDocumentRepository;
import com.dgcockpit.service.DocumentFinalizationService;
import com.dgcockpit.service.MinioService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pdf-documents")
@CrossOrigin(origins = "*")
public class PdfDocumentController {

    private final PdfDocumentRepository repo;
    private final MinioService minio;
    private final DocumentFinalizationService finalizer;

    public PdfDocumentController(PdfDocumentRepository repo,
                                 MinioService minio,
                                 DocumentFinalizationService finalizer) {
        this.repo = repo;
        this.minio = minio;
        this.finalizer = finalizer;
    }

    @GetMapping
    public List<PdfDocument> list() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping
    public ResponseEntity<PdfDocument> upload(@RequestParam MultipartFile file,
                                              @RequestParam(required = false) String title) throws Exception {
        String objectKey = UUID.randomUUID() + "_" + file.getOriginalFilename();
        minio.uploadBytes("ged-documents", objectKey, file.getBytes(), "application/pdf");

        int pageCount;
        try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
            pageCount = pdf.getNumberOfPages();
        }

        PdfDocument doc = new PdfDocument();
        doc.setTitle(title != null && !title.isBlank() ? title : file.getOriginalFilename());
        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setBucket("ged-documents");
        doc.setObjectKey(objectKey);
        doc.setPageCount(pageCount);
        doc.setStatus("DRAFT");

        return ResponseEntity.ok(repo.save(doc));
    }

    @GetMapping("/{id}/pages/{page}/image")
    public ResponseEntity<byte[]> renderPage(@PathVariable String id, @PathVariable int page) throws Exception {
        byte[] png = finalizer.renderPage(id, page);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<PdfDocument> finalize(@PathVariable String id) throws Exception {
        PdfDocument doc = finalizer.finalize(id);
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}/final")
    public ResponseEntity<byte[]> downloadFinal(@PathVariable String id) throws Exception {
        PdfDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
        byte[] bytes = finalizer.downloadFinal(id);
        String encoded = URLEncoder.encode(doc.getOriginalFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(bytes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) throws Exception {
        PdfDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
        minio.delete(doc.getBucket(), doc.getObjectKey());
        if (doc.getFinalizedObjectKey() != null) {
            minio.delete("ged-final-documents", doc.getFinalizedObjectKey());
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
