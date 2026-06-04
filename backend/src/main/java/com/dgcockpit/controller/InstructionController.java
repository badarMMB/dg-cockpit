package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.Assignee;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.entity.InstructionType;
import com.dgcockpit.repository.BureauDocumentRepository;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import com.dgcockpit.repository.InstructionTypeRepository;
import com.dgcockpit.service.AuthorizationService;
import com.dgcockpit.sse.SseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/instructions")
public class InstructionController {

    private final InstructionRepository instructionRepo;
    private final InstructionMessageRepository messageRepo;
    private final InstructionTypeRepository instructionTypeRepo;
    private final BureauDocumentRepository bureauDocRepo;
    private final SseService sseService;
    private final AuthorizationService authorizationService;

    public InstructionController(InstructionRepository instructionRepo,
                                 InstructionMessageRepository messageRepo,
                                 InstructionTypeRepository instructionTypeRepo,
                                 BureauDocumentRepository bureauDocRepo,
                                 SseService sseService,
                                 AuthorizationService authorizationService) {
        this.instructionRepo = instructionRepo;
        this.messageRepo = messageRepo;
        this.instructionTypeRepo = instructionTypeRepo;
        this.bureauDocRepo = bureauDocRepo;
        this.sseService = sseService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<Map<String, Object>> getAllInstructions(
            @RequestAttribute(value = "currentUser", required = false) AppUser currentUser) {
        if (currentUser == null) return List.of();
        // Règle stricte : on ne voit que les fils dont on est l'initiateur ou un assigné
        // Les instructions créées par l'utilisateur + celles où il est assigné
        LinkedHashSet<Instruction> found = new LinkedHashSet<>();
        found.addAll(instructionRepo.findByCreatedByIdOrderByCreatedAtDesc(currentUser.getId()));
        found.addAll(instructionRepo.findByAssigneeUserId(currentUser.getId()));
        found.addAll(instructionRepo.findByAssignee(currentUser.getNomComplet())); // compat legacy
        return found.stream().map(this::toThreadDto).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CAN_CREATE_INSTRUCTION')")
    public Map<String, Object> createInstruction(@RequestBody Map<String, Object> body,
                                                  HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        Instruction instruction = new Instruction();
        instruction.setTitle((String) body.getOrDefault("title", ""));
        if (currentUser != null) instruction.setCreatedById(currentUser.getId());

        // ── InstructionType FK ──────────────────────────────────────────
        String typeId = (String) body.get("instructionTypeId");
        InstructionType itype = null;
        if (typeId != null && !typeId.isBlank()) {
            itype = instructionTypeRepo.findById(typeId).orElse(null);
        }
        if (itype != null) {
            instruction.setInstructionType(itype);
            instruction.setType(itype.getLabel());
        } else {
            instruction.setType((String) body.getOrDefault("type", "Autre"));
        }

        // ── Urgence ─────────────────────────────────────────────────────
        String urgence = (String) body.get("urgence");
        if (urgence == null && itype != null) urgence = itype.getUrgenceDefaut().name();
        instruction.setUrgence(urgence != null ? urgence : "NORMAL");

        // ── Statut initial ───────────────────────────────────────────────
        instruction.setStatut(Instruction.StatutInstruction.OUVERT);

        // ── Confidentialité + Échéance ───────────────────────────────────
        instruction.setConfidentialite(Boolean.TRUE.equals(body.get("confidentialite")));
        String echeanceStr = (String) body.get("echeance");
        if (echeanceStr != null && !echeanceStr.isBlank()) {
            instruction.setEcheance(LocalDate.parse(echeanceStr));
        }

        // ── Assignees ────────────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assigneesData =
            (List<Map<String, Object>>) body.getOrDefault("assignees", List.of());
        String agentDisplay = assigneesData.size() > 1
            ? assigneesData.size() + " intervenants"
            : assigneesData.isEmpty() ? "Non assigné"
            : (String) assigneesData.get(0).getOrDefault("agent", "");
        instruction.setAgentDisplay(agentDisplay);

        Instruction saved = instructionRepo.save(instruction);

        for (Map<String, Object> a : assigneesData) {
            Assignee assignee = new Assignee();
            assignee.setInstruction(saved);
            assignee.setAgent((String) a.get("agent"));
            assignee.setUserId((String) a.get("userId")); // null pour les données legacy
            saved.getAssignees().add(assignee);
        }

        // ── Message initial ──────────────────────────────────────────────
        String msgText = buildInitialMessageText(body, assigneesData, itype, instruction);
        InstructionMessage msg = new InstructionMessage();
        msg.setInstruction(saved);
        String senderName = currentUser != null ? currentUser.getNomComplet() : "DG";
        msg.setSender(senderName);
        msg.setSelf(true);
        msg.setText(msgText);
        boolean hasAudio = Boolean.TRUE.equals(body.get("hasAudio"));
        msg.setAttachmentName(hasAudio ? "Memo_Vocal_DG.m4a" : null);
        messageRepo.save(msg);

        instructionRepo.save(saved);
        sseService.broadcast("INSTRUCTION_CREATED", Map.of(
            "id", saved.getId(),
            "title", saved.getTitle() != null ? saved.getTitle() : ""));
        return toThreadDto(saved);
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(
            @PathVariable String id,
            HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        Instruction instruction = instructionRepo.findById(id).orElse(null);
        if (instruction == null) return ResponseEntity.notFound().build();
        authorizationService.requireInstructionAccess(instruction);
        return ResponseEntity.ok(
            messageRepo.findByInstructionIdOrderBySentAtAsc(id).stream()
                .map(this::toMessageDto).toList());
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(@PathVariable String id,
                                            @RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        Instruction instruction = instructionRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Instruction introuvable: " + id));
        authorizationService.requireInstructionAccess(instruction);
        if (instruction.getStatut() == Instruction.StatutInstruction.CLOTURE)
            return ResponseEntity.status(403).body(Map.of("error", "Instruction clôturée"));

        InstructionMessage msg = new InstructionMessage();
        msg.setInstruction(instruction);

        String senderName = currentUser != null ? currentUser.getNomComplet()
            : (String) body.getOrDefault("sender", "Inconnu");
        boolean isSelf = Boolean.TRUE.equals(body.get("isSelf"));
        msg.setSender(senderName);
        msg.setSelf(isSelf);
        msg.setText((String) body.get("text"));

        String audioUrl = (String) body.get("audioUrl");
        if (audioUrl != null && !audioUrl.isBlank()) {
            msg.setAudioUrl(audioUrl);
            msg.setAttachmentName((String) body.getOrDefault("attachmentName", "voice.webm"));
        }

        // Passer EN_COURS dès le premier message d'un non-initiateur
        if (instruction.getStatut() == Instruction.StatutInstruction.OUVERT) {
            boolean isInitiateur = currentUser != null
                && currentUser.getId().equals(instruction.getCreatedById());
            if (!isInitiateur) {
                instruction.setStatut(Instruction.StatutInstruction.EN_COURS);
                instructionRepo.save(instruction);
            }
        }

        InstructionMessage saved = messageRepo.save(msg);
        sseService.broadcast("INSTRUCTION_UPDATED", Map.of(
            "id", id, "statut", instruction.getStatut().name()));
        return ResponseEntity.ok(toMessageDto(saved));
    }

    /**
     * Retourne les instructions DOCUMENTAIRE en attente (OUVERT/EN_COURS) assignées à l'utilisateur
     * courant et liées au binôme du TypeDocument donné.
     */
    @GetMapping("/pending-for-document-type/{typeDocId}")
    public List<Map<String, Object>> pendingForDocumentType(
            @PathVariable String typeDocId,
            HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        if (currentUser == null) return List.of();

        return instructionRepo.findPendingByTypeDocumentAndAssignee(
                typeDocId,
                List.of(Instruction.StatutInstruction.OUVERT, Instruction.StatutInstruction.EN_COURS),
                currentUser.getId()
        ).stream().map(i -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", i.getId());
            m.put("title", i.getTitle());
            m.put("statut", i.getStatut().name());
            m.put("urgence", i.getUrgence());
            return m;
        }).toList();
    }

    /** Clôture manuelle — réservée à l'initiateur ou au DG. */
    @PostMapping("/{id}/cloturer")
    @SuppressWarnings("deprecation")
    public ResponseEntity<Map<String, Object>> cloturer(@PathVariable String id,
                                                         HttpServletRequest request) {
        AppUser currentUser = (AppUser) request.getAttribute("currentUser");
        Instruction instruction = instructionRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Instruction introuvable: " + id));
        authorizationService.requireInstructionInitiator(instruction);

        instruction.setStatut(Instruction.StatutInstruction.CLOTURE);
        instructionRepo.save(instruction);
        sseService.broadcast("INSTRUCTION_UPDATED", Map.of("id", id, "statut", "CLOTURE"));
        return ResponseEntity.ok(toThreadDto(instruction));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    private Map<String, Object> toThreadDto(Instruction i) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", i.getId());
        m.put("title", i.getTitle() != null ? i.getTitle() : "Sans titre");
        m.put("type", i.getType() != null ? i.getType() : "");
        m.put("agent", i.getAgentDisplay() != null ? i.getAgentDisplay() : "—");
        m.put("date", i.getCreatedAt().toLocalDate().toString());
        m.put("statut", i.getStatut() != null ? i.getStatut().name() : "OUVERT");
        m.put("unread", 0);
        m.put("urgence", i.getUrgence() != null ? i.getUrgence() : "NORMAL");
        m.put("confidentialite", i.isConfidentialite());
        m.put("echeance", i.getEcheance() != null ? i.getEcheance().toString() : null);
        m.put("createdById", i.getCreatedById());
        m.put("documentCree", bureauDocRepo.existsBySourceInstructionId(i.getId()));
        if (i.getInstructionType() != null) {
            InstructionType itype = i.getInstructionType();
            m.put("instructionTypeId", itype.getId());
            m.put("typeInstruction", itype.getTypeInstruction().name());
            m.put("typeDocumentAttenduId", itype.getTypeDocumentAttenduId());
            m.put("typeCategorie", itype.getCategorie().name());
        } else {
            m.put("instructionTypeId", null);
            m.put("typeInstruction", "LIBRE");
            m.put("typeDocumentAttenduId", null);
            m.put("typeCategorie", null);
        }
        return m;
    }

    private Map<String, Object> toMessageDto(InstructionMessage m) {
        var map = new HashMap<String, Object>();
        map.put("id", m.getId());
        map.put("sender", m.getSender());
        map.put("isSelf", m.isSelf());
        map.put("text", m.getText());
        map.put("time", m.getSentAt().toLocalTime().toString().substring(0, 5));
        map.put("hasAttachment", m.getAttachmentName() != null);
        map.put("attachmentName", m.getAttachmentName());
        map.put("audioUrl", m.getAudioUrl());
        map.put("highlightsJson", m.getHighlightsJson());
        map.put("systemMessage", m.isSystemMessage());
        return map;
    }

    private String buildInitialMessageText(Map<String, Object> body,
                                            List<Map<String, Object>> assignees,
                                            InstructionType itype,
                                            Instruction instruction) {
        StringBuilder sb = new StringBuilder();
        String typeLabel = itype != null ? itype.getLabel() : (String) body.getOrDefault("type", "Autre");
        sb.append("[Type : ").append(typeLabel).append("]");
        if (instruction.getUrgence() != null && !"NORMAL".equals(instruction.getUrgence())) {
            sb.append(" — ").append("URGENT".equals(instruction.getUrgence()) ? "🔴 URGENT" : "📅 Planifié");
        }
        if (instruction.getEcheance() != null) {
            sb.append("\n[Échéance : ").append(instruction.getEcheance()).append("]");
        }
        if (instruction.isConfidentialite()) sb.append("\n[🔒 Confidentiel]");
        if (!assignees.isEmpty()) {
            sb.append("\n[Intervenants : ");
            sb.append(assignees.stream()
                .map(a -> (String) a.getOrDefault("agent", ""))
                .reduce((a, b) -> a + ", " + b).orElse(""));
            sb.append("]");
        }
        String text = (String) body.get("message");
        if (text != null && !text.isBlank()) sb.append("\n\n").append(text);
        return sb.toString();
    }
}
