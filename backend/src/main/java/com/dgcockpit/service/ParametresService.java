package com.dgcockpit.service;

import com.dgcockpit.entity.*;
import com.dgcockpit.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ParametresService {

    private final InstructionTypeRepository instructionTypeRepo;
    private final ProofTypeRepository proofTypeRepo;
    private final AppUserRepository userRepo;
    private final PosteRepository posteRepo;
    private final WorkflowStepRepository workflowStepRepo;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public ParametresService(InstructionTypeRepository instructionTypeRepo,
                             ProofTypeRepository proofTypeRepo,
                             AppUserRepository userRepo,
                             PosteRepository posteRepo,
                             WorkflowStepRepository workflowStepRepo,
                             AuthService authService,
                             ObjectMapper objectMapper) {
        this.instructionTypeRepo = instructionTypeRepo;
        this.proofTypeRepo       = proofTypeRepo;
        this.userRepo            = userRepo;
        this.posteRepo           = posteRepo;
        this.workflowStepRepo    = workflowStepRepo;
        this.authService         = authService;
        this.objectMapper        = objectMapper;
    }

    // ── InstructionType ───────────────────────────────────────────────────────

    public List<InstructionType> getAllInstructionTypes() {
        return instructionTypeRepo.findAllByOrderByCategorieAscLabelAsc();
    }

    public List<InstructionType> getActiveInstructionTypes() {
        return instructionTypeRepo.findByActifTrueOrderByCategorieAscLabelAsc();
    }

    public InstructionType createInstructionType(Map<String, Object> body) {
        InstructionType t = new InstructionType();
        applyInstructionTypeFields(t, body);
        return instructionTypeRepo.save(t);
    }

    public InstructionType updateInstructionType(String id, Map<String, Object> body) {
        InstructionType t = instructionTypeRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("InstructionType introuvable: " + id));
        applyInstructionTypeFields(t, body);
        return instructionTypeRepo.save(t);
    }

    public void toggleInstructionType(String id) {
        InstructionType t = instructionTypeRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("InstructionType introuvable: " + id));
        t.setActif(!t.isActif());
        instructionTypeRepo.save(t);
    }

    public void deleteInstructionType(String id) {
        instructionTypeRepo.deleteById(id);
    }

    private void applyInstructionTypeFields(InstructionType t, Map<String, Object> body) {
        if (body.containsKey("code"))
            t.setCode((String) body.get("code"));
        if (body.containsKey("label"))
            t.setLabel((String) body.get("label"));
        if (body.containsKey("categorie"))
            t.setCategorie(InstructionType.Categorie.valueOf((String) body.get("categorie")));
        if (body.containsKey("urgenceDefaut"))
            t.setUrgenceDefaut(InstructionType.Urgence.valueOf((String) body.get("urgenceDefaut")));
        if (body.containsKey("livrableAttendu"))
            t.setLivrableAttendu(InstructionType.TypeLivrable.valueOf((String) body.get("livrableAttendu")));
        if (body.containsKey("actif"))
            t.setActif(Boolean.TRUE.equals(body.get("actif")));
        if (body.containsKey("documentsAttendus")) {
            Object val = body.get("documentsAttendus");
            try {
                t.setDocumentsAttendus(val == null ? null : objectMapper.writeValueAsString(val));
            } catch (Exception e) {
                t.setDocumentsAttendus("[]");
            }
        }
    }

    // ── WorkflowStep ──────────────────────────────────────────────────────────

    public List<WorkflowStep> getStepsForType(String instructionTypeId) {
        return workflowStepRepo.findByInstructionTypeIdOrderByStepOrderAsc(instructionTypeId);
    }

    public WorkflowStep createStep(String instructionTypeId, Map<String, Object> body) {
        InstructionType type = instructionTypeRepo.findById(instructionTypeId)
            .orElseThrow(() -> new RuntimeException("InstructionType introuvable: " + instructionTypeId));
        WorkflowStep step = new WorkflowStep();
        step.setInstructionType(type);
        applyStepFields(step, body);
        return workflowStepRepo.save(step);
    }

    public WorkflowStep updateStep(String stepId, Map<String, Object> body) {
        WorkflowStep step = workflowStepRepo.findById(stepId)
            .orElseThrow(() -> new RuntimeException("WorkflowStep introuvable: " + stepId));
        applyStepFields(step, body);
        return workflowStepRepo.save(step);
    }

    public void deleteStep(String stepId) {
        workflowStepRepo.deleteById(stepId);
    }

    private void applyStepFields(WorkflowStep step, Map<String, Object> body) {
        if (body.containsKey("stepOrder"))
            step.setStepOrder(((Number) body.get("stepOrder")).intValue());
        if (body.containsKey("stepLabel"))
            step.setStepLabel((String) body.get("stepLabel"));
        if (body.containsKey("requiresSignature"))
            step.setRequiresSignature(Boolean.TRUE.equals(body.get("requiresSignature")));
        if (body.containsKey("requiresAttachment"))
            step.setRequiresAttachment(Boolean.TRUE.equals(body.get("requiresAttachment")));
        if (body.containsKey("timeoutJours")) {
            Object v = body.get("timeoutJours");
            step.setTimeoutJours(v == null ? null : ((Number) v).intValue());
        }
        if (body.containsKey("actorInstructions"))
            step.setActorInstructions((String) body.get("actorInstructions"));
        if (body.containsKey("requiredPosteId")) {
            String posteId = (String) body.get("requiredPosteId");
            step.setRequiredPoste(posteId != null ? posteRepo.findById(posteId).orElse(null) : null);
        }
    }

    // ── ProofType ─────────────────────────────────────────────────────────────

    public List<ProofType> getAllProofTypes() {
        return proofTypeRepo.findAllByOrderByLabelAsc();
    }

    public List<ProofType> getActiveProofTypes() {
        return proofTypeRepo.findByActifTrueOrderByLabelAsc();
    }

    public ProofType createProofType(Map<String, Object> body) {
        ProofType p = new ProofType();
        applyProofTypeFields(p, body);
        return proofTypeRepo.save(p);
    }

    public ProofType updateProofType(String id, Map<String, Object> body) {
        ProofType p = proofTypeRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("ProofType introuvable: " + id));
        applyProofTypeFields(p, body);
        return proofTypeRepo.save(p);
    }

    public void toggleProofType(String id) {
        ProofType p = proofTypeRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("ProofType introuvable: " + id));
        p.setActif(!p.isActif());
        proofTypeRepo.save(p);
    }

    public void deleteProofType(String id) {
        proofTypeRepo.deleteById(id);
    }

    private void applyProofTypeFields(ProofType p, Map<String, Object> body) {
        if (body.containsKey("label"))
            p.setLabel((String) body.get("label"));
        if (body.containsKey("acceptedFormats"))
            p.setAcceptedFormats((String) body.get("acceptedFormats"));
        if (body.containsKey("description"))
            p.setDescription((String) body.get("description"));
        if (body.containsKey("actif"))
            p.setActif(Boolean.TRUE.equals(body.get("actif")));
    }

    // ── Poste ─────────────────────────────────────────────────────────────────

    public List<Poste> getAllPostes() {
        return posteRepo.findAllByOrderByLibelleAsc();
    }

    public List<Poste> getActivePostes() {
        return posteRepo.findByActifTrueOrderByLibelleAsc();
    }

    public Poste createPoste(Map<String, Object> body) {
        Poste p = new Poste();
        applyPosteFields(p, body);
        return posteRepo.save(p);
    }

    public Poste updatePoste(String id, Map<String, Object> body) {
        Poste p = posteRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Poste introuvable: " + id));
        applyPosteFields(p, body);
        return posteRepo.save(p);
    }

    public void togglePoste(String id) {
        Poste p = posteRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Poste introuvable: " + id));
        p.setActif(!p.isActif());
        posteRepo.save(p);
    }

    public void deletePoste(String id) {
        posteRepo.deleteById(id);
    }

    @SuppressWarnings("unchecked")
    private void applyPosteFields(Poste p, Map<String, Object> body) {
        if (body.containsKey("code"))
            p.setCode((String) body.get("code"));
        if (body.containsKey("libelle"))
            p.setLibelle((String) body.get("libelle"));
        if (body.containsKey("actif"))
            p.setActif(Boolean.TRUE.equals(body.get("actif")));
        if (body.containsKey("habilitations")) {
            List<String> habStrings = (List<String>) body.get("habilitations");
            Set<Poste.Habilitation> habs = habStrings == null
                ? Set.of()
                : habStrings.stream()
                    .map(Poste.Habilitation::valueOf)
                    .collect(Collectors.toSet());
            p.setHabilitations(habs);
        }
    }

    // ── AppUser ───────────────────────────────────────────────────────────────

    public List<AppUser> getAllUsers() {
        return userRepo.findAllByOrderByNomCompletAsc();
    }

    @SuppressWarnings("deprecation")
    public AppUser createUser(Map<String, Object> body) {
        AppUser u = new AppUser();
        u.setUsername((String) body.get("username"));
        u.setNomComplet((String) body.get("nomComplet"));
        if (body.containsKey("role") && body.get("role") != null) {
            u.setRole(AppUser.Role.valueOf((String) body.get("role")));
        }
        String password = (String) body.getOrDefault("password", "changeme");
        u.setPasswordHash(authService.hashPassword(password));
        if (body.containsKey("posteId") && body.get("posteId") != null) {
            posteRepo.findById((String) body.get("posteId")).ifPresent(u::setPoste);
        }
        return userRepo.save(u);
    }

    @SuppressWarnings("deprecation")
    public AppUser updateUser(String id, Map<String, Object> body) {
        AppUser u = userRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable: " + id));
        if (body.containsKey("nomComplet"))
            u.setNomComplet((String) body.get("nomComplet"));
        if (body.containsKey("role") && body.get("role") != null)
            u.setRole(AppUser.Role.valueOf((String) body.get("role")));
        if (body.containsKey("actif"))
            u.setActif(Boolean.TRUE.equals(body.get("actif")));
        if (body.containsKey("posteId")) {
            String posteId = (String) body.get("posteId");
            u.setPoste(posteId != null ? posteRepo.findById(posteId).orElse(null) : null);
        }
        return userRepo.save(u);
    }

    public void resetPassword(String id, String newPassword) {
        AppUser u = userRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable: " + id));
        u.setPasswordHash(authService.hashPassword(newPassword));
        userRepo.save(u);
    }

    public void toggleUser(String id) {
        AppUser u = userRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable: " + id));
        u.setActif(!u.isActif());
        userRepo.save(u);
    }

    public void deleteUser(String id) {
        userRepo.deleteById(id);
    }
}
