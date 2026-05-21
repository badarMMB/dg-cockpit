package com.dgcockpit.controller;

import com.dgcockpit.entity.Assignee;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.entity.InstructionType;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import com.dgcockpit.repository.InstructionTypeRepository;
import com.dgcockpit.sse.SseService;
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

    public InstructionController(InstructionRepository instructionRepo,
                                 InstructionMessageRepository messageRepo,
                                 InstructionTypeRepository instructionTypeRepo,
                                 SseService sseService) {
        this.instructionRepo = instructionRepo;
        this.messageRepo = messageRepo;
        this.instructionTypeRepo = instructionTypeRepo;
        this.sseService = sseService;
    }

    @GetMapping
    public List<Map<String, Object>> getAllInstructions(@RequestAttribute(value = "currentUser", required = false) com.dgcockpit.entity.AppUser currentUser) {
        if (currentUser != null && "SUBORDONNE".equals(currentUser.getRole().name())) {
            return instructionRepo.findByAssignee(currentUser.getNomComplet()).stream()
                .map(this::toThreadDto)
                .toList();
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

    @GetMapping("/{id}/messages")
    public List<Map<String, Object>> getMessages(@PathVariable String id) {
        return messageRepo.findByInstructionIdOrderBySentAtAsc(id).stream()
            .map(this::toMessageDto)
            .toList();
    }

    @PostMapping("/{id}/messages")
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
            String actionTypeStr = (String) body.get("actionType");
            if (actionTypeStr != null) {
                msg.setActionType(InstructionMessage.ActionType.valueOf(actionTypeStr.toUpperCase()));
            }
            msg.setStatut(InstructionMessage.StatutMessage.PENDING);
            msg.setAttachmentName("Document_Final.pdf");
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
    public Map<String, Object> validateMessage(@PathVariable String msgId) {
        return updateMessageStatut(msgId, InstructionMessage.StatutMessage.VALIDATED);
    }

    @PatchMapping("/messages/{msgId}/reject")
    public Map<String, Object> rejectMessage(@PathVariable String msgId) {
        return updateMessageStatut(msgId, InstructionMessage.StatutMessage.REJECTED);
    }

    private Map<String, Object> updateMessageStatut(String msgId, InstructionMessage.StatutMessage statut) {
        InstructionMessage msg = messageRepo.findById(msgId)
            .orElseThrow(() -> new RuntimeException("Message introuvable: " + msgId));
        msg.setStatut(statut);
        Instruction instr = msg.getInstruction();
        if (statut == InstructionMessage.StatutMessage.VALIDATED) {
            instr.setStatut(Instruction.StatutInstruction.CLOTURE);
            instructionRepo.save(instr);
        }
        Map<String, Object> result = toMessageDto(messageRepo.save(msg));
        sseService.broadcast("INSTRUCTION_UPDATED", Map.of(
            "id", instr.getId(), "statut", instr.getStatut().name()));
        return result;
    }

    private Map<String, Object> toThreadDto(Instruction i) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", i.getId());
        m.put("title", i.getTitle() != null ? i.getTitle() : "Sans titre");
        m.put("type", i.getType() != null ? i.getType() : "");
        m.put("agent", i.getAgentDisplay() != null ? i.getAgentDisplay() : "—");
        m.put("date", i.getCreatedAt().toLocalDate().toString());
        m.put("statut", i.getStatut().name());
        m.put("unread", 0);
        m.put("urgence", i.getUrgence() != null ? i.getUrgence() : "NORMAL");
        m.put("confidentialite", i.isConfidentialite());
        m.put("echeance", i.getEcheance() != null ? i.getEcheance().toString() : null);
        if (i.getInstructionType() != null) {
            m.put("instructionTypeId", i.getInstructionType().getId());
            m.put("typeLivrable", i.getInstructionType().getLivrableAttendu().name());
            m.put("typeCategorie", i.getInstructionType().getCategorie().name());
        } else {
            m.put("instructionTypeId", null);
            m.put("typeLivrable", null);
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
