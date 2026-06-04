package com.dgcockpit.controller;

import com.dgcockpit.entity.CourrierArrive;
import com.dgcockpit.entity.CourrierDepart;
import com.dgcockpit.entity.Instruction;
import com.dgcockpit.repository.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final InstructionRepository instructionRepo;
    private final CourrierArriveRepository courrierArriveRepo;
    private final CourrierDepartRepository courrierDepartRepo;
    private final RendezVousRepository rendezVousRepo;

    public DashboardController(InstructionRepository instructionRepo,
                               CourrierArriveRepository courrierArriveRepo,
                               CourrierDepartRepository courrierDepartRepo,
                               RendezVousRepository rendezVousRepo) {
        this.instructionRepo = instructionRepo;
        this.courrierArriveRepo = courrierArriveRepo;
        this.courrierDepartRepo = courrierDepartRepo;
        this.rendezVousRepo = rendezVousRepo;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long instructionsActives = instructionRepo.countByStatutIn(
            List.of(Instruction.StatutInstruction.OUVERT,
                    Instruction.StatutInstruction.EN_COURS)
        );
        long docsASignerCount = 0L;

        LocalDate today = LocalDate.now();
        long rdvAujourdhui = rendezVousRepo.findByDateBetweenOrderByDateAscHeureAsc(today, today).size();

        // Instructions par statut
        var instrByStatut = new HashMap<String, Long>();
        for (Instruction.StatutInstruction s : Instruction.StatutInstruction.values()) {
            instrByStatut.put(s.name(), instructionRepo.countByStatut(s));
        }

        // Courriers arrivés par statut
        var caByStatut = new HashMap<String, Long>();
        for (CourrierArrive.Statut s : CourrierArrive.Statut.values()) {
            caByStatut.put(s.name(), courrierArriveRepo.countByStatut(s));
        }

        // Courriers départ par statut
        var cdByStatut = new HashMap<String, Long>();
        for (CourrierDepart.Statut s : CourrierDepart.Statut.values()) {
            cdByStatut.put(s.name(), courrierDepartRepo.countByStatut(s));
        }

        var result = new HashMap<String, Object>();
        result.put("instructionsActives", instructionsActives);
        result.put("documentsASigner", docsASignerCount);
        result.put("rendezVousDuJour", rdvAujourdhui);
        result.put("totalInstructions", instructionRepo.count());
        result.put("totalCourrierArrive", courrierArriveRepo.count());
        result.put("totalCourrierDepart", courrierDepartRepo.count());
        result.put("instructionsByStatut", instrByStatut);
        result.put("courrierArriveByStatut", caByStatut);
        result.put("courrierDepartByStatut", cdByStatut);
        return result;
    }

    @GetMapping("/instructions-recentes")
    public List<Map<String, Object>> getInstructionsRecentes() {
        return instructionRepo.findAllByOrderByCreatedAtDesc()
            .stream().limit(5)
            .map(i -> Map.<String, Object>of(
                "id", i.getId(),
                "title", i.getTitle() != null ? i.getTitle() : "Sans titre",
                "agent", i.getAgentDisplay() != null ? i.getAgentDisplay() : "—",
                "statut", i.getStatut().name()
            ))
            .toList();
    }
}
