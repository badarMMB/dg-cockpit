package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.BureauDocument;
import com.dgcockpit.entity.CircuitSignature;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.entity.PageAnnotation;
import com.dgcockpit.entity.PdfDocument;
import com.dgcockpit.entity.TypeDocument;
import com.dgcockpit.repository.AppUserRepository;
import com.dgcockpit.repository.BureauDocumentRepository;
import com.dgcockpit.repository.CircuitSignatureRepository;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import com.dgcockpit.repository.PageAnnotationRepository;
import com.dgcockpit.repository.PdfDocumentRepository;
import com.dgcockpit.repository.TypeDocumentRepository;
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
    private final CircuitSignatureRepository circuitRepo;
    private final AppUserRepository userRepo;
    private final InstructionRepository instructionRepo;
    private final InstructionMessageRepository instructionMessageRepo;
    private final TypeDocumentRepository typeDocRepo;
    private final MinioService minio;
    private final DocumentFinalizationService finalizer;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    public BureauController(BureauDocumentRepository bureauRepo,
                            PdfDocumentRepository pdfRepo,
                            PageAnnotationRepository annotRepo,
                            CircuitSignatureRepository circuitRepo,
                            AppUserRepository userRepo,
                            InstructionRepository instructionRepo,
                            InstructionMessageRepository instructionMessageRepo,
                            TypeDocumentRepository typeDocRepo,
                            MinioService minio,
                            DocumentFinalizationService finalizer,
                            SseService sseService,
                            ObjectMapper objectMapper) {
        this.bureauRepo = bureauRepo;
        this.pdfRepo = pdfRepo;
        this.annotRepo = annotRepo;
        this.circuitRepo = circuitRepo;
        this.userRepo = userRepo;
        this.instructionRepo = instructionRepo;
        this.instructionMessageRepo = instructionMessageRepo;
        this.typeDocRepo = typeDocRepo;
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
            @RequestParam(required = false) String typeDocumentId,
            HttpServletRequest request) throws Exception {

        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        if (currentUser == null || !currentUser.hasBureau()) return ResponseEntity.status(403).build();
        String proprietaireId = currentUser.getId();

        minio.ensureBucket(BUCKET);
        String objectKey = UUID.randomUUID() + "_" + file.getOriginalFilename();
        minio.uploadBytes(BUCKET, objectKey, file.getBytes(), "application/pdf");

        int pageCount = finalizer.getPageCount(BUCKET, objectKey);

        // Résoudre le code type depuis le TypeDocument si fourni
        String codeType = type;
        if (typeDocumentId != null && !typeDocumentId.isBlank()) {
            TypeDocument td = typeDocRepo.findById(typeDocumentId).orElse(null);
            if (td != null) codeType = td.getCode();
        }

        BureauDocument doc = new BureauDocument();
        doc.setProprietaireId(proprietaireId);
        doc.setTitre(titre != null && !titre.isBlank() ? titre : file.getOriginalFilename());
        doc.setType(codeType);
        doc.setDestinataire(destinataire);
        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setBucket(BUCKET);
        doc.setObjectKey(objectKey);
        doc.setPageCount(pageCount);
        if (typeDocumentId != null && !typeDocumentId.isBlank()) {
            doc.setTypeDocumentId(typeDocumentId);
        }

        return ResponseEntity.ok(toDto(bureauRepo.save(doc)));
    }

    // ── GET /api/bureau/documents ── liste des documents du bureau de l'utilisateur connecté
    @GetMapping("/documents")
    public List<Map<String, Object>> list(HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        if (currentUser == null) return List.of();
        // Tout utilisateur peut voir ses propres docs (transit inclus), même sans hasBureau
        String proprietaireId = currentUser.getId();
        return bureauRepo.findByProprietaireIdOrderByCreatedAtDesc(proprietaireId)
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
        if (doc.getStatut() == BureauDocument.Statut.RETOURNE) {
            doc.setCorrigeDepuisRenvoi(true);
        }

        return ResponseEntity.ok(toDto(bureauRepo.save(doc)));
    }

    // ── DELETE /api/bureau/documents/:id
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) {
        BureauDocument doc = bureauRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BureauDocument introuvable: " + id));
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        String proprietaireId = currentUser != null ? currentUser.getId() : "";
        if (!doc.getProprietaireId().equals(proprietaireId)) return ResponseEntity.status(403).build();
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
            @RequestParam(required = false) String circuit,
            HttpServletRequest request) {

        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        if (currentUser == null) return ResponseEntity.status(403).build();

        BureauDocument doc = bureauRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BureauDocument introuvable: " + id));

        // Les docs de transit (circuit) sont accessibles à tout utilisateur authentifié
        if (!currentUser.hasBureau() && doc.getCircuitPdfDocumentId() == null) {
            return ResponseEntity.status(403).build();
        }

        if (doc.getStatut() == BureauDocument.Statut.SOUMIS) {
            return ResponseEntity.badRequest().build();
        }

        final boolean isResoumission = doc.getStatut() == BureauDocument.Statut.RETOURNE;
        if (isResoumission) {
            doc.setRenvoyeMotif(null);
            doc.setHighlightsJson(null);
            doc.setCorrigeDepuisRenvoi(false);
        }

        List<Map<String, Object>> sigZones = parseZones(doc.getSignatureZonesJson());
        if (sigZones.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String submittedBy = currentUser.getUsername();

        // ── Document de transit : avancer le circuit existant sans créer un nouveau PdfDocument ──
        if (doc.getCircuitPdfDocumentId() != null) {
            PdfDocument pdfDoc = pdfRepo.findById(doc.getCircuitPdfDocumentId())
                    .orElseThrow(() -> new IllegalArgumentException("PdfDocument de circuit introuvable: "
                            + doc.getCircuitPdfDocumentId()));

            if (pdfDoc.getParapheurStatut() != PdfDocument.ParapheurStatut.EN_ATTENTE_TRANSMISSION) {
                return ResponseEntity.badRequest().build();
            }

            int nextStep = doc.getCircuitNextStep();
            CircuitSignature nextEtape = circuitRepo.findByPdfDocumentIdAndStepOrder(
                    doc.getCircuitPdfDocumentId(), nextStep)
                    .orElseThrow(() -> new IllegalArgumentException("Étape circuit introuvable: step " + nextStep));

            // Supprimer les anciennes annotations (déjà brûlées dans la version signée précédente)
            for (int p = 0; p < pdfDoc.getPageCount(); p++) {
                annotRepo.deleteAll(annotRepo.findByPageId(pdfDoc.getId() + "::" + p));
            }

            // Créer les nouvelles annotations de signature depuis les zones du bureau de transit
            for (Map<String, Object> z : sigZones) {
                PageAnnotation a = new PageAnnotation();
                a.setPageId(pdfDoc.getId() + "::" + toInt(z.get("page")));
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
                a.setPageId(pdfDoc.getId() + "::" + toInt(z.get("page")));
                a.setAnnotationType("STAMP_ZONE");
                a.setXPercent(toDouble(z.get("x")));
                a.setYPercent(toDouble(z.get("y")));
                a.setWidthPercent(toDouble(z.get("w")));
                a.setHeightPercent(toDouble(z.get("h")));
                a.setCreatedBy(submittedBy);
                annotRepo.save(a);
            }

            // Activer l'étape suivante du circuit
            pdfDoc.setCurrentSignataireUserId(nextEtape.getSignaireUserId());
            pdfDoc.setCurrentCircuitStep(nextStep);
            pdfDoc.setParapheurStatut(PdfDocument.ParapheurStatut.EN_ATTENTE_SIGNATURE);
            pdfDoc.setUpdatedAt(LocalDateTime.now());
            pdfRepo.save(pdfDoc);

            // Marquer le bureau de transit comme soumis
            doc.setStatut(BureauDocument.Statut.SOUMIS);
            doc.setSoumisAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            bureauRepo.save(doc);

            sseService.broadcast("PARAPHEUR_UPDATED", Map.of("action", "SOUMIS", "id", pdfDoc.getId()));

            Map<String, Object> transitResult = new HashMap<>();
            transitResult.put("bureauDocumentId", doc.getId());
            transitResult.put("pdfDocumentId", pdfDoc.getId());
            transitResult.put("statut", "SOUMIS");
            return ResponseEntity.ok(transitResult);
        }

        // ── Résolution du circuit de signature (document normal) ────────────

        // Charger le TypeDocument lié s'il existe
        TypeDocument typeDoc = doc.getTypeDocumentId() != null
                ? typeDocRepo.findById(doc.getTypeDocumentId()).orElse(null)
                : null;

        // Déterminer le circuit selon le mode du TypeDocument
        List<Map<String, Object>> circuitSteps;
        if (typeDoc != null && typeDoc.getModeCircuit() == TypeDocument.ModeCircuit.MANAGER_SEUL) {
            // Forcer le manager direct, ignorer le picker
            AppUser manager = currentUser.getManager();
            if (manager == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Aucun supérieur hiérarchique défini. Contactez l'administrateur."));
            }
            circuitSteps = List.of(Map.of("userId", manager.getId(), "nom", manager.getNomComplet()));
        } else if (typeDoc != null && typeDoc.getModeCircuit() == TypeDocument.ModeCircuit.PREDEFINI
                && (circuit == null || circuit.isBlank())) {
            // Circuit fixé par le type — résoudre les postes → utilisateurs
            circuitSteps = resoudreCircuitPredefini(typeDoc.getCircuitJson());
            if (circuitSteps.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Circuit prédéfini vide ou mal configuré pour ce type de document."));
            }
        } else {
            // LIBRE, PREDEFINI_MODIFIABLE ou fallback : utiliser le circuit fourni / manager
            circuitSteps = resoudreCircuit(circuit, currentUser);
            if (circuitSteps.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Aucun supérieur hiérarchique défini. Contactez l'administrateur."));
            }
        }

        // ActionFinale → ParapheurType
        boolean publier = typeDoc != null
                ? typeDoc.getActionFinale() == TypeDocument.ActionFinale.PUBLIER
                : "NOTE_SERVICE".equals(doc.getType());

        PdfDocument.ParapheurType parapheurType = publier
                ? PdfDocument.ParapheurType.NOTE_SERVICE
                : PdfDocument.ParapheurType.COURRIER;

        PdfDocument pdf = new PdfDocument();
        pdf.setTitle(doc.getTitre());
        pdf.setOriginalFileName(doc.getOriginalFileName());
        pdf.setBucket(doc.getBucket());
        pdf.setObjectKey(doc.getObjectKey());
        Integer pageCount = doc.getPageCount();
        pdf.setPageCount(pageCount != null ? pageCount : 1);
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

        // ── Créer les étapes du circuit de signature ────────────────────────
        // Les étapes > 0 héritent des zones du document source pour que le prochain
        // signataire ait une zone pré-positionnée au même endroit (pratique sur doc 1 page).
        String zonesJsonSource = doc.getSignatureZonesJson();
        for (int i = 0; i < circuitSteps.size(); i++) {
            Map<String, Object> step = circuitSteps.get(i);
            CircuitSignature etape = new CircuitSignature();
            etape.setPdfDocumentId(savedPdf.getId());
            etape.setStepOrder(i);
            etape.setSignaireUserId((String) step.get("userId"));
            etape.setSignaireNom((String) step.get("nom"));
            // Étape 0 : annotations déjà créées via PageAnnotation — pas besoin de les dupliquer.
            // Étapes suivantes : propager les zones du document source (même position).
            etape.setSignatureZonesJson(i == 0 ? null : zonesJsonSource);
            circuitRepo.save(etape);
        }
        // Pointer le PdfDocument vers le premier signataire
        savedPdf.setCurrentSignataireUserId((String) circuitSteps.get(0).get("userId"));
        savedPdf.setCurrentCircuitStep(0);
        pdfRepo.save(savedPdf);

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

        sseService.broadcast("PARAPHEUR_UPDATED", Map.of(
            "action", "SOUMIS", "id", savedPdf.getId()));

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
        m.put("corrigeDepuisRenvoi", d.isCorrigeDepuisRenvoi());
        m.put("circuitPdfDocumentId", d.getCircuitPdfDocumentId());
        m.put("typeDocumentId", d.getTypeDocumentId());
        // Exposer le mode circuit pour que le frontend adapte la modale de soumission
        if (d.getTypeDocumentId() != null) {
            typeDocRepo.findById(d.getTypeDocumentId()).ifPresent(td -> {
                m.put("modeCircuit", td.getModeCircuit().name());
                m.put("requiresSignatureZone", td.isRequiresSignatureZone());
                m.put("requiresStampZone", td.isRequiresStampZone());
                m.put("requiresDestinataire", td.isRequiresDestinataire());
                m.put("typeDocCircuit", parseZones(td.getCircuitJson()));
            });
        }
        m.put("highlights", parseZones(d.getHighlightsJson()));
        m.put("reference", d.getReference());
        m.put("soumisAt",   d.getSoumisAt()   != null ? d.getSoumisAt().toString()   : null);
        m.put("signeAt",    d.getSigneAt()    != null ? d.getSigneAt().toString()    : null);
        m.put("retourneAt", d.getRetourneAt() != null ? d.getRetourneAt().toString() : null);
        m.put("livreAt",    d.getLivreAt()    != null ? d.getLivreAt().toString()    : null);
        return m;
    }

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

    /**
     * Résout la liste ordonnée de signataires pour le circuit.
     * Si {@code circuitJson} est fourni et non vide, il est utilisé tel quel.
     * Sinon, le circuit par défaut est [{manager de currentUser}].
     * Retourne une liste vide si aucun supérieur n'est défini.
     */
    private List<Map<String, Object>> resoudreCircuit(String circuitJson, AppUser currentUser) {
        if (circuitJson != null && !circuitJson.isBlank()) {
            try {
                List<Map<String, Object>> steps = objectMapper.readValue(
                        circuitJson, new TypeReference<List<Map<String, Object>>>() {});
                if (!steps.isEmpty()) return steps;
            } catch (Exception ignored) {}
        }
        AppUser manager = currentUser.getManager();
        if (manager == null) return List.of();
        return List.of(Map.of("userId", manager.getId(), "nom", manager.getNomComplet()));
    }

    /**
     * Résout un circuit pré-défini (liste de postes) en liste de signataires concrets.
     * Chaque entrée JSON : {posteId, posteLibelle}. Le premier utilisateur actif du poste est retenu.
     */
    private List<Map<String, Object>> resoudreCircuitPredefini(String circuitJson) {
        if (circuitJson == null || circuitJson.isBlank()) return List.of();
        try {
            List<Map<String, Object>> etapes = objectMapper.readValue(
                    circuitJson, new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> etape : etapes) {
                String posteId = (String) etape.get("posteId");
                if (posteId == null) continue;
                List<AppUser> occupants = userRepo.findByPosteIdAndActifTrue(posteId);
                if (occupants.isEmpty()) continue;
                AppUser u = occupants.get(0);
                result.add(Map.of("userId", u.getId(), "nom", u.getNomComplet()));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}
