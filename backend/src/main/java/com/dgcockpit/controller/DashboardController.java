package com.dgcockpit.controller;

import com.dgcockpit.entity.Instruction;
import com.dgcockpit.entity.InstructionMessage;
import com.dgcockpit.repository.CollaborateurRepository;
import com.dgcockpit.repository.InstructionMessageRepository;
import com.dgcockpit.repository.InstructionRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final InstructionRepository instructionRepo;
    private final InstructionMessageRepository messageRepo;
    private final CollaborateurRepository collaborateurRepo;

    public DashboardController(InstructionRepository instructionRepo,
                               InstructionMessageRepository messageRepo,
                               CollaborateurRepository collaborateurRepo) {
        this.instructionRepo = instructionRepo;
        this.messageRepo = messageRepo;
        this.collaborateurRepo = collaborateurRepo;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long instructionsActives = instructionRepo.countByStatutIn(
            List.of(Instruction.StatutInstruction.OUVERT, Instruction.StatutInstruction.EN_COURS, Instruction.StatutInstruction.EN_ATTENTE)
        );
        long docsASignerCount = messageRepo.countByTypeAndStatut(
            InstructionMessage.TypeMessage.FINAL, InstructionMessage.StatutMessage.PENDING
        );
        return Map.of(
            "instructionsActives", instructionsActives,
            "documentsASigner", docsASignerCount,
            "rendezVousDuJour", 3
        );
    }

    @GetMapping("/instructions-recentes")
    public List<Map<String, Object>> getInstructionsRecentes() {
        return instructionRepo.findAllByOrderByCreatedAtDesc()
            .stream().limit(5)
            .map(i -> Map.of(
                "id", i.getId(),
                "title", i.getTitle() != null ? i.getTitle() : "Sans titre",
                "agent", i.getAgentDisplay() != null ? i.getAgentDisplay() : "—",
                "statut", i.getStatut().name()
            ))
            .toList();
    }
}
