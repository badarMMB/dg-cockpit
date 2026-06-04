package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.Poste;
import com.dgcockpit.entity.TypeDocument;
import com.dgcockpit.repository.TypeDocumentRepository;
import com.dgcockpit.service.CollaboraConvertService;
import com.dgcockpit.service.DocxToHtmlConverter;
import com.dgcockpit.service.MinioService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD des types de documents.
 * Accès réservé aux utilisateurs DG ou possédant l'habilitation CAN_MANAGE_TYPES.
 */
@RestController
@RequestMapping("/api/type-documents")
@CrossOrigin(origins = "*")
public class TypeDocumentController {

    private final TypeDocumentRepository repo;
    private final ObjectMapper objectMapper;
    private final DocxToHtmlConverter docxConverter;
    private final MinioService minioService;
    private final CollaboraConvertService collaboraConvertService;

    @Value("${minio.bucket}")
    private String defaultBucket;

    public TypeDocumentController(TypeDocumentRepository repo,
                                  ObjectMapper objectMapper,
                                  DocxToHtmlConverter docxConverter,
                                  MinioService minioService,
                                  CollaboraConvertService collaboraConvertService) {
        this.repo                    = repo;
        this.objectMapper            = objectMapper;
        this.docxConverter           = docxConverter;
        this.minioService            = minioService;
        this.collaboraConvertService = collaboraConvertService;
    }

    // ── GET /api/type-documents ── liste (admin: tous ; autres: actifs uniquement) ──

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        boolean isAdmin = estAdmin(currentUser);
        List<TypeDocument> types = isAdmin
                ? repo.findAllByOrderByLibelleAsc()
                : repo.findByActifTrueOrderByLibelleAsc();
        return types.stream().map(this::toDto).toList();
    }

    // ── GET /api/type-documents/{id} ── récupérer par ID ──────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(
            @PathVariable String id,
            HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        boolean isAdmin = estAdmin(currentUser);

        TypeDocument t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TypeDocument introuvable: " + id));

        // Accès: admin (tous) ou utilisateur non-admin (seulement si actif)
        if (!isAdmin && !t.isActif()) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(toDto(t));
    }

    // ── POST /api/type-documents ── créer ──────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        if (!estAdmin(currentUser)) return ResponseEntity.status(403).build();

        TypeDocument t = new TypeDocument();
        appliquerBody(t, body);
        return ResponseEntity.ok(toDto(repo.save(t)));
    }

    // ── PUT /api/type-documents/{id} ── modifier ───────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        if (!estAdmin(currentUser)) return ResponseEntity.status(403).build();

        TypeDocument t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TypeDocument introuvable: " + id));
        appliquerBody(t, body);
        t.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(toDto(repo.save(t)));
    }

    // ── PATCH /api/type-documents/{id}/toggle ── activer / désactiver ──────────

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Void> toggle(@PathVariable String id, HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        if (!estAdmin(currentUser)) return ResponseEntity.status(403).build();

        TypeDocument t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TypeDocument introuvable: " + id));
        t.setActif(!t.isActif());
        t.setUpdatedAt(LocalDateTime.now());
        repo.save(t);
        return ResponseEntity.noContent().build();
    }

    // ── DELETE /api/type-documents/{id} ───────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        if (!estAdmin(currentUser)) return ResponseEntity.status(403).build();

        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── POST /api/type-documents/convert-template ─────────────────────────────
    // Accepte un fichier .docx, le convertit en HTML propre (Mammoth + JSoup),
    // sauve l'original dans MinIO et renvoie { html, docxPath }.

    @PostMapping("/convert-template")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Map<String, String>> convertTemplate(
            @RequestParam("file") MultipartFile file) throws Exception {

        String original = file.getOriginalFilename();
        if (file.isEmpty() || original == null || !original.toLowerCase().endsWith(".docx")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Un fichier .docx est requis"));
        }

        String html       = docxConverter.convert(file);
        String objectKey  = "templates/" + UUID.randomUUID() + ".docx";
        String stored     = minioService.upload(file, objectKey);

        return ResponseEntity.ok(Map.of("html", html, "docxPath", stored));
    }

    // ── POST /api/type-documents/upload-template ──────────────────────────────
    // Stocke le .docx dans MinIO et pré-génère le PDF de prévisualisation.
    // Retourne { docxPath, pdfPath, fileName }.

    @PostMapping("/upload-template")
    @PreAuthorize("hasAuthority('CAN_MANAGE_TYPES')")
    public ResponseEntity<Map<String, String>> uploadTemplate(
            @RequestParam("file") MultipartFile file) throws Exception {

        String original = file.getOriginalFilename();
        if (file.isEmpty() || original == null || !original.toLowerCase().endsWith(".docx")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Un fichier .docx est requis"));
        }

        String uuid      = UUID.randomUUID().toString();
        String docxKey   = "templates/" + uuid + ".docx";
        String pdfKey    = "templates/" + uuid + ".pdf";

        minioService.upload(file, docxKey);

        byte[] docxBytes = file.getBytes();
        byte[] pdfBytes  = collaboraConvertService.docxToPdf(docxBytes, original);
        minioService.uploadBytes(defaultBucket, pdfKey, pdfBytes, "application/pdf");

        return ResponseEntity.ok(Map.of("docxPath", docxKey, "pdfPath", pdfKey, "fileName", original));
    }

    // ── GET /api/type-documents/{id}/template-preview ─────────────────────────
    // Sert le PDF pré-converti depuis MinIO (instantané).
    // Fallback : re-convertit à la volée si templatePdfPath absent.

    @GetMapping("/{id}/template-preview")
    public ResponseEntity<byte[]> templatePreview(
            @PathVariable String id,
            HttpServletRequest request) throws Exception {

        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        if (!estAdmin(currentUser)) return ResponseEntity.status(403).build();

        TypeDocument t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TypeDocument introuvable: " + id));

        if (t.getTemplateDocxPath() == null || t.getTemplateDocxPath().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdfBytes;
        if (t.getTemplatePdfPath() != null && !t.getTemplatePdfPath().isBlank()) {
            pdfBytes = minioService.downloadBytes(defaultBucket, t.getTemplatePdfPath());
        } else {
            byte[] docxBytes = minioService.downloadBytes(defaultBucket, t.getTemplateDocxPath());
            pdfBytes = collaboraConvertService.docxToPdf(docxBytes, t.getLibelle() + ".docx");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private boolean estAdmin(AppUser u) {
        if (u == null) return false;
        if (AppUser.Role.DG.equals(u.getRole())) return true;
        return u.hasHabilitation(Poste.Habilitation.CAN_MANAGE_TYPES);
    }

    private void appliquerBody(TypeDocument t, Map<String, Object> body) {
        if (body.containsKey("code"))
            t.setCode((String) body.get("code"));
        if (body.containsKey("libelle"))
            t.setLibelle((String) body.get("libelle"));
        if (body.containsKey("modeCircuit")) {
            try { t.setModeCircuit(TypeDocument.ModeCircuit.valueOf((String) body.get("modeCircuit"))); }
            catch (Exception ignored) {}
        }
        if (body.containsKey("actionFinale")) {
            try { t.setActionFinale(TypeDocument.ActionFinale.valueOf((String) body.get("actionFinale"))); }
            catch (Exception ignored) {}
        }
        if (body.containsKey("circuitJson")) {
            Object raw = body.get("circuitJson");
            t.setCircuitJson(raw == null ? null : toJson(raw));
        }
        if (body.containsKey("initiateurPostesJson")) {
            Object raw = body.get("initiateurPostesJson");
            t.setInitiateurPostesJson(raw == null ? null : toJson(raw));
        }
        if (body.containsKey("requiresSignatureZone"))
            t.setRequiresSignatureZone(Boolean.TRUE.equals(body.get("requiresSignatureZone")));
        if (body.containsKey("requiresStampZone"))
            t.setRequiresStampZone(Boolean.TRUE.equals(body.get("requiresStampZone")));
        if (body.containsKey("requiresDestinataire"))
            t.setRequiresDestinataire(Boolean.TRUE.equals(body.get("requiresDestinataire")));
        if (body.containsKey("templateHtml"))
            t.setTemplateHtml((String) body.get("templateHtml"));
        if (body.containsKey("templateDocxPath"))
            t.setTemplateDocxPath((String) body.get("templateDocxPath"));
        if (body.containsKey("templatePdfPath"))
            t.setTemplatePdfPath((String) body.get("templatePdfPath"));
        if (body.containsKey("actif"))
            t.setActif(Boolean.TRUE.equals(body.get("actif")));
    }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return null; }
    }

    Map<String, Object> toDto(TypeDocument t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("libelle", t.getLibelle());
        m.put("modeCircuit", t.getModeCircuit().name());
        m.put("actionFinale", t.getActionFinale().name());
        m.put("circuit", parseJson(t.getCircuitJson()));
        m.put("initiateurPostes", parseJson(t.getInitiateurPostesJson()));
        m.put("requiresSignatureZone", t.isRequiresSignatureZone());
        m.put("requiresStampZone", t.isRequiresStampZone());
        m.put("requiresDestinataire", t.isRequiresDestinataire());
        m.put("templateHtml", t.getTemplateHtml());
        m.put("templateDocxPath", t.getTemplateDocxPath());
        m.put("templatePdfPath", t.getTemplatePdfPath());
        m.put("actif", t.isActif());
        m.put("createdAt", t.getCreatedAt().toString());
        return m;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return objectMapper.readValue(json, new TypeReference<Object>() {}); }
        catch (Exception e) { return new ArrayList<>(); }
    }
}
