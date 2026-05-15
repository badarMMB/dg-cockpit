package com.dgcockpit.controller;

import com.dgcockpit.entity.Assignee;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/instructions")
public class InstructionController {

    private final InstructionRepository instructionRepo;
    private final InstructionMessageRepository messageRepo;

    public InstructionController(InstructionRepository instructionRepo,
                                 InstructionMessageRepository messageRepo) {
        this.instructionRepo = instructionRepo;
        this.messageRepo = messageRepo;
    }

    @GetMapping
    public List<Map<String, Object>> getAllInstructions() {
        return instructionRepo.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toThreadDto)
            .toList();
    }

    @PostMapping
    public Map<String, Object> createInstruction(@RequestBody Map<String, Object> body) {
        Instruction instruction = new Instruction();
        instruction.setTitle((String) body.getOrDefault("title", ""));
        instruction.setType((String) body.getOrDefault("type", "Autre"));

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

        String msgText = buildInitialMessageText(body, assigneesData);
        InstructionMessage msg = new InstructionMessage();
        msg.setInstruction(saved);
        msg.setSender("DG");
        msg.setSelf(true);
        msg.setText(msgText);
        boolean hasAudio = Boolean.TRUE.equals(body.get("hasAudio"));
        msg.setAttachmentName(hasAudio ? "Memo_Vocal_DG.m4a" : null);
        messageRepo.save(msg);

        instructionRepo.save(saved);
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
        if (statut == InstructionMessage.StatutMessage.VALIDATED) {
            msg.getInstruction().setStatut(Instruction.StatutInstruction.CLOTURE);
            instructionRepo.save(msg.getInstruction());
        }
        return toMessageDto(messageRepo.save(msg));
    }

    private Map<String, Object> toThreadDto(Instruction i) {
        return Map.of(
            "id", i.getId(),
            "title", i.getTitle() != null ? i.getTitle() : "Sans titre",
            "agent", i.getAgentDisplay() != null ? i.getAgentDisplay() : "—",
            "date", i.getCreatedAt().toLocalTime().toString().substring(0, 5),
            "statut", i.getStatut().name(),
            "unread", 0
        );
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
        return map;
    }

    private String buildInitialMessageText(Map<String, Object> body, List<Map<String, String>> assignees) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Type: ").append(body.getOrDefault("type", "Autre")).append("]\n");
        if (!assignees.isEmpty()) {
            sb.append("[Attentes: ");
            sb.append(assignees.stream()
                .map(a -> a.get("agent") + " (" + a.getOrDefault("role", "avis") + ")")
                .reduce((a, b) -> a + ", " + b).orElse(""));
            sb.append("]\n");
        }
        String text = (String) body.get("message");
        if (text != null && !text.isBlank()) {
            sb.append("\n").append(text);
        }
        return sb.toString();
    }
}
