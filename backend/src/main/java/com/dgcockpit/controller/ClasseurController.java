package com.dgcockpit.controller;

import com.dgcockpit.entity.BureauDocument;
import com.dgcockpit.entity.Classeur;
import com.dgcockpit.entity.ClasseurDocument;
import com.dgcockpit.entity.CourrierDepart;
import com.dgcockpit.repository.BureauDocumentRepository;
import com.dgcockpit.repository.ClasseurDocumentRepository;
import com.dgcockpit.repository.ClasseurRepository;
import com.dgcockpit.repository.CourrierDepartRepository;
import com.dgcockpit.service.MinioService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/classeurs")
@CrossOrigin(origins = "*")
public class ClasseurController {

    private static final String SCAN_BUCKET = "ged-livraison-scans";

    private final ClasseurRepository classeurRepo;
    private final ClasseurDocumentRepository classeurDocRepo;
    private final CourrierDepartRepository courrierDepartRepo;
    private final BureauDocumentRepository bureauDocRepo;
    private final MinioService minio;

    public ClasseurController(ClasseurRepository classeurRepo,
                              ClasseurDocumentRepository classeurDocRepo,
                              CourrierDepartRepository courrierDepartRepo,
                              BureauDocumentRepository bureauDocRepo,
                              MinioService minio) {
        this.classeurRepo       = classeurRepo;
        this.classeurDocRepo    = classeurDocRepo;
        this.courrierDepartRepo = courrierDepartRepo;
        this.bureauDocRepo      = bureauDocRepo;
        this.minio              = minio;
    }

    // ── GET /api/classeurs ── liste tous les classeurs avec compteur
    @GetMapping
    public List<Map<String, Object>> list() {
        return classeurRepo.findAllByOrderByCreatedAtAsc().stream()
                .map(this::toDto)
                .toList();
    }

    // ── POST /api/classeurs ── créer un classeur
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        String nom = body.get("nom");
        if (nom == null || nom.isBlank()) return ResponseEntity.badRequest().build();

        Classeur c = new Classeur();
        c.setNom(nom.trim());
        if (body.get("type") != null) {
            try { c.setType(Classeur.Type.valueOf(body.get("type"))); } catch (Exception ignored) {}
        }
        if (body.get("description") != null) c.setDescription(body.get("description"));
        if (body.get("couleur") != null) c.setCouleur(body.get("couleur"));

        return ResponseEntity.ok(toDto(classeurRepo.save(c)));
    }

    // ── GET /api/classeurs/:id ── détail d'un classeur
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        return classeurRepo.findById(id)
                .map(c -> ResponseEntity.ok(toDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DELETE /api/classeurs/:id ── supprimer (non-système uniquement)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        Classeur c = classeurRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classeur introuvable: " + id));
        if (c.isSysteme()) return ResponseEntity.status(403).build();
        classeurDocRepo.findByClasseurIdOrderByClasseAtDesc(id).forEach(classeurDocRepo::delete);
        classeurRepo.delete(c);
        return ResponseEntity.noContent().build();
    }

    // ── GET /api/classeurs/:id/documents ── documents d'un classeur
    @GetMapping("/{id}/documents")
    public List<Map<String, Object>> documents(@PathVariable String id) {
        return classeurDocRepo.findByClasseurIdOrderByClasseAtDesc(id).stream()
                .map(this::docToDto)
                .toList();
    }

    // ── POST /api/classeurs/livraison/{courrierDepartId} ── marquer livré + scan + classement
    @PostMapping("/livraison/{courrierDepartId}")
    public ResponseEntity<Map<String, Object>> livrer(
            @PathVariable String courrierDepartId,
            @RequestParam MultipartFile scan,
            @RequestParam String classeurIds) throws Exception {

        CourrierDepart courrier = courrierDepartRepo.findById(courrierDepartId)
                .orElseThrow(() -> new IllegalArgumentException("Courrier introuvable: " + courrierDepartId));

        if (courrier.getStatut() != CourrierDepart.Statut.SIGNE) {
            return ResponseEntity.badRequest().build();
        }

        // Upload du scan dans MinIO
        minio.ensureBucket(SCAN_BUCKET);
        String scanKey = UUID.randomUUID() + "_" + scan.getOriginalFilename();
        minio.uploadBytes(SCAN_BUCKET, scanKey, scan.getBytes(),
                scan.getContentType() != null ? scan.getContentType() : "application/octet-stream");

        // Mise à jour du statut
        courrier.setStatut(CourrierDepart.Statut.LIVRE);
        courrier.setScanLivraisonKey(scanKey);
        courrierDepartRepo.save(courrier);

        // Classement dans chaque classeur sélectionné
        List<String> ids = Arrays.stream(classeurIds.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();

        List<Map<String, Object>> classements = new ArrayList<>();
        for (String cId : ids) {
            classeurRepo.findById(cId).ifPresent(classeur -> {
                ClasseurDocument cd = new ClasseurDocument();
                cd.setClasseur(classeur);
                cd.setDocumentType(ClasseurDocument.DocumentType.COURRIER_DEPART);
                cd.setDocumentId(courrier.getId());
                cd.setTitre(courrier.getObjet());
                cd.setReference(courrier.getReference());
                cd.setDestinataire(courrier.getDestinataire());
                cd.setDateDocument(courrier.getDateEnvoi());
                cd.setScanKey(scanKey);
                classeurDocRepo.save(cd);
                classements.add(docToDto(cd));
            });
        }

        Map<String, Object> result = new HashMap<>();
        result.put("courrierDepartId", courrier.getId());
        result.put("statut", "LIVRE");
        result.put("scanKey", scanKey);
        result.put("classements", classements);
        return ResponseEntity.ok(result);
    }

    // ── POST /api/classeurs/livraison-bureau/{bureauDocId} ── classer + scan pour un BureauDocument
    @PostMapping("/livraison-bureau/{bureauDocId}")
    public ResponseEntity<Map<String, Object>> livrerBureau(
            @PathVariable String bureauDocId,
            @RequestParam MultipartFile scan,
            @RequestParam String classeurIds) throws Exception {

        BureauDocument doc = bureauDocRepo.findById(bureauDocId)
                .orElseThrow(() -> new IllegalArgumentException("BureauDocument introuvable: " + bureauDocId));

        if (doc.getStatut() != BureauDocument.Statut.SIGNE) {
            return ResponseEntity.badRequest().build();
        }

        // Upload scan dans MinIO
        minio.ensureBucket(SCAN_BUCKET);
        String scanKey = UUID.randomUUID() + "_" + scan.getOriginalFilename();
        minio.uploadBytes(SCAN_BUCKET, scanKey, scan.getBytes(),
                scan.getContentType() != null ? scan.getContentType() : "application/octet-stream");

        // Mise à jour statut
        doc.setStatut(BureauDocument.Statut.LIVRE);
        doc.setScanLivraisonKey(scanKey);
        bureauDocRepo.save(doc);

        // Classement dans chaque classeur sélectionné
        List<String> ids = Arrays.stream(classeurIds.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();

        List<Map<String, Object>> classements = new ArrayList<>();
        for (String cId : ids) {
            classeurRepo.findById(cId).ifPresent(classeur -> {
                ClasseurDocument cd = new ClasseurDocument();
                cd.setClasseur(classeur);
                cd.setDocumentType(ClasseurDocument.DocumentType.PDF_DOCUMENT);
                cd.setDocumentId(doc.getId());
                cd.setTitre(doc.getTitre());
                cd.setDestinataire(doc.getDestinataire());
                cd.setScanKey(scanKey);
                classeurDocRepo.save(cd);
                classements.add(docToDto(cd));
            });
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bureauDocumentId", doc.getId());
        result.put("statut", "LIVRE");
        result.put("scanKey", scanKey);
        result.put("classements", classements);
        return ResponseEntity.ok(result);
    }

    // ── GET /api/classeurs/scan/:key ── servir le scan de livraison
    @GetMapping("/scan/{key}")
    public ResponseEntity<byte[]> getScan(@PathVariable String key) throws Exception {
        byte[] bytes = minio.downloadBytes(SCAN_BUCKET, key);
        String ct = key.toLowerCase().endsWith(".pdf") ? "application/pdf" : "image/jpeg";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + key + "\"")
                .body(bytes);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    private Map<String, Object> toDto(Classeur c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("nom", c.getNom());
        m.put("type", c.getType().name());
        m.put("description", c.getDescription());
        m.put("couleur", c.getCouleur());
        m.put("systeme", c.isSysteme());
        m.put("createdAt", c.getCreatedAt().toString());
        m.put("documentCount", classeurDocRepo.countByClasseurId(c.getId()));
        return m;
    }

    private Map<String, Object> docToDto(ClasseurDocument cd) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", cd.getId());
        m.put("classeurId", cd.getClasseur().getId());
        m.put("documentType", cd.getDocumentType().name());
        m.put("documentId", cd.getDocumentId());
        m.put("titre", cd.getTitre());
        m.put("reference", cd.getReference());
        m.put("destinataire", cd.getDestinataire());
        m.put("dateDocument", cd.getDateDocument() != null ? cd.getDateDocument().toString() : null);
        m.put("scanKey", cd.getScanKey());
        m.put("scanUrl", cd.getScanKey() != null ? "/api/classeurs/scan/" + cd.getScanKey() : null);
        m.put("classeAt", cd.getClasseAt().toString());
        return m;
    }
}
