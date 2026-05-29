package com.dgcockpit.controller;

import com.dgcockpit.entity.Assignee;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.entity.InstructionType;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import com.dgcockpit.repository.InstructionTypeRepository;
import com.dgcockpit.sse.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/instructions")
public class InstructionController {

    private final InstructionRepository instructionRepo;
    private final InstructionMessageRepository messageRepo;
    private final InstructionTypeRepository instructionTypeRepo;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    public InstructionController(InstructionRepository instructionRepo,
                                 InstructionMessageRepository messageRepo,
                                 InstructionTypeRepository instructionTypeRepo,
                                 SseService sseService,
                                 ObjectMapper objectMapper) {
        this.instructionRepo = instructionRepo;
        this.messageRepo = messageRepo;
        this.instructionTypeRepo = instructionTypeRepo;
        this.sseService = sseService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @SuppressWarnings("deprecation")
    public List<Map<String, Object>> getAllInstructions(@RequestAttribute(value = "currentUser", required = false) com.dgcockpit.entity.AppUser currentUser) {
        if (currentUser != null) {
            boolean canViewAll = currentUser.hasHabilitation(com.dgcockpit.entity.Poste.Habilitation.CAN_VIEW_ALL)
                || (currentUser.getRole() != null
                    && currentUser.getRole() != com.dgcockpit.entity.AppUser.Role.SUBORDONNE);
            if (!canViewAll) {
                return instructionRepo.findByAssignee(currentUser.getNomComplet()).stream()
                    .map(this::toThreadDto)
                    .toList();
            }
        }
        return instructionRepo.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toThreadDto)
            .toList();
    }

    @PostMapping
    public Map<String, Object> createInstruction(@RequestBody Map<String, Object> body) {
        Instruction instruction = new Instruction();
        instruction.setTitle((String) body.getOrDefault("title", ""));

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
        if (urgence == null && itype != null) {
            urgence = itype.getUrgenceDefaut().name();
        }
        instruction.setUrgence(urgence != null ? urgence : "NORMAL");

        // ── Statut initial ───────────────────────────────────────────────
        instruction.setGlobalStatus(Instruction.GlobalStatus.BROUILLON);
        @SuppressWarnings({"deprecation", "java:S1874"})
        Instruction.StatutInstruction statutInitial = Instruction.StatutInstruction.OUVERT;
        instruction.setStatut(statutInitial);

        // ── Confidentialité + Échéance ───────────────────────────────────
        instruction.setConfidentialite(Boolean.TRUE.equals(body.get("confidentialite")));
        String echeanceStr = (String) body.get("echeance");
        if (echeanceStr != null && !echeanceStr.isBlank()) {
            instruction.setEcheance(LocalDate.parse(echeanceStr));
        }

        // ── Assignees ────────────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        List<Map<String, String>> assigneesData = (List<Map<String, String>>) body.getOrDefault("assignees", List.of());
        String agentDisplay = assigneesData.size() > 1
            ? assigneesData.size() + " intervenants"
            : assigneesData.isEmpty() ? "Non assigné" : assigneesData.get(0).get("agent");
        instruction.setAgentDisplay(agentDisplay);

        Instruction saved = instructionRepo.save(instruction);

        for (Map<String, String> a : assigneesData) {
            Assignee assignee = new Assignee();
            assignee.setInstruction(saved);
            assignee.setAgent(a.get("agent"));
            try {
                assignee.setRole(Assignee.RoleAssignee.valueOf(a.get("role").toUpperCase()));
            } catch (Exception ignored) {
                assignee.setRole(Assignee.RoleAssignee.AVIS_SIMPLE);
            }
            saved.getAssignees().add(assignee);
        }

        // ── Initial message ──────────────────────────────────────────────
        String msgText = buildInitialMessageText(body, assigneesData, itype, instruction);
        InstructionMessage msg = new InstructionMessage();
        msg.setInstruction(saved);
        msg.setSender("DG");
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

    /**
     * Rétro-compatibilité : passage en SOUMIS_VALIDATION (ancien workflow subordonné).
     * Conservé pendant la migration vers le nouveau moteur de circuit.
     */
    @PostMapping("/{id}/soumettre")
    @SuppressWarnings({"deprecation", "java:S1874"})
    public ResponseEntity<Map<String, Object>> soumettre(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        Instruction instruction = instructionRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Instruction introuvable: " + id));
        instruction.setStatut(Instruction.StatutInstruction.SOUMIS_VALIDATION);
        instructionRepo.save(instruction);
        sseService.broadcast("INSTRUCTION_UPDATED", Map.of(
            "id",     id,
            "statut", Instruction.StatutInstruction.SOUMIS_VALIDATION.name()
        ));
        return ResponseEntity.ok(Map.of("id", id, "statut", "SOUMIS_VALIDATION"));
    }

    @GetMapping("/{id}/messages")
    public List<Map<String, Object>> getMessages(@PathVariable String id) {
        return messageRepo.findByInstructionIdOrderBySentAtAsc(id).stream()
            .map(this::toMessageDto)
            .toList();
    }

    @PostMapping("/{id}/messages")
    @SuppressWarnings({"deprecation", "java:S1874"})
    public Map<String, Object> sendMessage(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Instruction instruction = instructionRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Instruction introuvable: " + id));

        InstructionMessage msg = new InstructionMessage();
        msg.setInstruction(instruction);
        msg.setSender((String) body.getOrDefault("sender", "DG"));
        msg.setSelf(Boolean.TRUE.equals(body.get("isSelf")));
        msg.setText((String) body.get("text"));

        String typeStr = (String) body.getOrDefault("type", "NORMAL");
        msg.setType(InstructionMessage.TypeMessage.valueOf(typeStr.toUpperCase()));

        if ("FINAL".equalsIgnoreCase(typeStr)) {
            if (instruction.getStatut() == Instruction.StatutInstruction.CLOTURE) {
                return toMessageDto(messageRepo.save(msg));
            }
            String actionTypeStr = (String) body.get("actionType");
            if (actionTypeStr != null) {
                msg.setActionType(InstructionMessage.ActionType.valueOf(actionTypeStr.toUpperCase()));
            }
            msg.setStatut(InstructionMessage.StatutMessage.PENDING);
            String bodyAttachment = (String) body.get("attachmentName");
            msg.setAttachmentName(bodyAttachment != null && !bodyAttachment.isBlank() ? bodyAttachment : null);
            instruction.setStatut(Instruction.StatutInstruction.EN_ATTENTE);
            instructionRepo.save(instruction);
        }

        String audioUrl = (String) body.get("audioUrl");
        if (audioUrl != null && !audioUrl.isBlank()) {
            msg.setAudioUrl(audioUrl);
            msg.setAttachmentName((String) body.getOrDefault("attachmentName", "voice.webm"));
        }

        InstructionMessage saved = messageRepo.save(msg);
        return toMessageDto(saved);
    }

    @PatchMapping("/messages/{msgId}/validate")
    @SuppressWarnings({"deprecation", "java:S1874"})
    public ResponseEntity<Map<String, Object>> validateMessage(@PathVariable String msgId, HttpServletRequest request) {
        com.dgcockpit.entity.AppUser currentUser = (com.dgcockpit.entity.AppUser) request.getAttribute("currentUser");
        if (!peutValiderMessages(currentUser)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(updateMessageStatut(msgId, InstructionMessage.StatutMessage.VALIDATED));
    }

    @PatchMapping("/messages/{msgId}/reject")
    @SuppressWarnings({"deprecation", "java:S1874"})
    public ResponseEntity<Map<String, Object>> rejectMessage(@PathVariable String msgId, HttpServletRequest request) {
        com.dgcockpit.entity.AppUser currentUser = (com.dgcockpit.entity.AppUser) request.getAttribute("currentUser");
        if (!peutValiderMessages(currentUser)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(updateMessageStatut(msgId, InstructionMessage.StatutMessage.REJECTED));
    }

    @SuppressWarnings("deprecation")
    private boolean peutValiderMessages(com.dgcockpit.entity.AppUser user) {
        if (user == null) return false;
        return user.hasHabilitation(com.dgcockpit.entity.Poste.Habilitation.CAN_VALIDATE)
            || (user.getRole() != null && user.getRole() == com.dgcockpit.entity.AppUser.Role.DG);
    }

    @SuppressWarnings({"deprecation", "removal", "java:S1874"})
    private Map<String, Object> updateMessageStatut(String msgId, InstructionMessage.StatutMessage statut) {
        InstructionMessage msg = messageRepo.findById(msgId)
            .orElseThrow(() -> new RuntimeException("Message introuvable: " + msgId));
        msg.setStatut(statut);
        Instruction instr = msg.getInstruction();
        if (statut == InstructionMessage.StatutMessage.VALIDATED) {
            instr.setStatut(Instruction.StatutInstruction.CLOTURE);
            instructionRepo.save(instr);
        } else if (statut == InstructionMessage.StatutMessage.REJECTED) {
            instr.setStatut(Instruction.StatutInstruction.REFUSE);
            instructionRepo.save(instr);
        }
        Map<String, Object> result = toMessageDto(messageRepo.save(msg));
        sseService.broadcast("INSTRUCTION_UPDATED", Map.of(
            "id", instr.getId(), "statut", instr.getStatut().name()));
        return result;
    }

    @SuppressWarnings({"deprecation", "java:S1874"})
    private Map<String, Object> toThreadDto(Instruction i) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", i.getId());
        m.put("title", i.getTitle() != null ? i.getTitle() : "Sans titre");
        m.put("type", i.getType() != null ? i.getType() : "");
        m.put("agent", i.getAgentDisplay() != null ? i.getAgentDisplay() : "—");
        m.put("date", i.getCreatedAt().toLocalDate().toString());
        m.put("statut",       i.getStatut()       != null ? i.getStatut().name()       : "OUVERT");
        m.put("globalStatus", i.getGlobalStatus() != null ? i.getGlobalStatus().name() : "BROUILLON");
        if (i.getCurrentStep() != null) {
            m.put("currentStepLabel", i.getCurrentStep().getStepLabel());
            m.put("currentStepOrder", i.getCurrentStep().getStepOrder());
            if (i.getCurrentStep().getRequiredPoste() != null) {
                m.put("currentStepPosteLibelle", i.getCurrentStep().getRequiredPoste().getLibelle());
            }
        }
        m.put("unread", 0);
        m.put("urgence", i.getUrgence() != null ? i.getUrgence() : "NORMAL");
        m.put("confidentialite", i.isConfidentialite());
        m.put("echeance", i.getEcheance() != null ? i.getEcheance().toString() : null);
        if (i.getInstructionType() != null) {
            InstructionType itype = i.getInstructionType();
            m.put("instructionTypeId", itype.getId());
            m.put("typeLivrable", itype.getLivrableAttendu().name());
            m.put("typeCategorie", itype.getCategorie().name());
            List<?> docs = List.of();
            String docsJson = itype.getDocumentsAttendus();
            if (docsJson != null && !docsJson.isBlank()) {
                try { docs = objectMapper.readValue(docsJson, List.class); } catch (Exception ignored) {}
            }
            m.put("documentsAttendus", docs);
        } else {
            m.put("instructionTypeId", null);
            m.put("typeLivrable", null);
            m.put("typeCategorie", null);
            m.put("documentsAttendus", List.of());
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
        map.put("type", m.getType().name().toLowerCase());
        map.put("hasAttachment", m.getAttachmentName() != null);
        map.put("attachmentName", m.getAttachmentName());
        map.put("actionType", m.getActionType() != null ? m.getActionType().name().toLowerCase() : null);
        map.put("status", m.getStatut() != null ? m.getStatut().name().toLowerCase() : null);
        map.put("audioUrl", m.getAudioUrl());
        map.put("highlightsJson", m.getHighlightsJson());
        return map;
    }

    private String buildInitialMessageText(Map<String, Object> body,
                                            List<Map<String, String>> assignees,
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
        if (instruction.isConfidentialite()) {
            sb.append("\n[🔒 Confidentiel]");
        }
        if (!assignees.isEmpty()) {
            sb.append("\n[Intervenants : ");
            sb.append(assignees.stream()
                .map(a -> a.get("agent") + " (" + a.getOrDefault("role", "avis") + ")")
                .reduce((a, b) -> a + ", " + b).orElse(""));
            sb.append("]");
        }
        String text = (String) body.get("message");
        if (text != null && !text.isBlank()) {
            sb.append("\n\n").append(text);
        }
        return sb.toString();
    }
}
