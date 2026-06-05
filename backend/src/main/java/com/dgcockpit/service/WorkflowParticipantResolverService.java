package com.dgcockpit.service;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.RoleParticipant;
import com.dgcockpit.entity.WorkflowParticipant;
import com.dgcockpit.repository.AppUserRepository;
import com.dgcockpit.repository.WorkflowParticipantRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Résout les WorkflowParticipant d'une étape en AppUser concrets.
 * RAPPORTEUR → premier utilisateur actif du poste.
 * PARTICIPANT / VALIDATEUR / SIGNATAIRE / DECIDEUR / RESPONSABLE / OBSERVATEUR → tous.
 */
@Service
public class WorkflowParticipantResolverService {

    private final WorkflowParticipantRepository participantRepo;
    private final AppUserRepository userRepo;

    public WorkflowParticipantResolverService(WorkflowParticipantRepository participantRepo,
                                               AppUserRepository userRepo) {
        this.participantRepo = participantRepo;
        this.userRepo = userRepo;
    }

    public List<AppUser> resolveParticipants(String stepId) {
        List<WorkflowParticipant> templates = participantRepo.findByWorkflowStepId(stepId);
        List<AppUser> result = new ArrayList<>();
        for (WorkflowParticipant wp : templates) {
            List<AppUser> candidats = userRepo.findByPosteIdAndActifTrue(wp.getPosteId());
            if (candidats.isEmpty()) continue;
            if (wp.getRoleParticipant() == RoleParticipant.RAPPORTEUR) {
                result.add(candidats.get(0));
            } else {
                result.addAll(candidats);
            }
        }
        return result;
    }

    /** Retourne uniquement les participants d'un rôle précis. */
    public List<AppUser> resolveByRole(String stepId, RoleParticipant role) {
        return participantRepo.findByWorkflowStepId(stepId).stream()
            .filter(wp -> wp.getRoleParticipant() == role)
            .flatMap(wp -> {
                List<AppUser> candidats = userRepo.findByPosteIdAndActifTrue(wp.getPosteId());
                return role == RoleParticipant.RAPPORTEUR
                    ? candidats.stream().limit(1)
                    : candidats.stream();
            })
            .toList();
    }
}
