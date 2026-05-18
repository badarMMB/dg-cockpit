package com.dgcockpit.service;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.InstructionType;
import com.dgcockpit.entity.ProofType;
import com.dgcockpit.repository.AppUserRepository;
import com.dgcockpit.repository.InstructionTypeRepository;
import com.dgcockpit.repository.ProofTypeRepository;
import com.dgcockpit.service.AuthService;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class ParametresService {

    private final InstructionTypeRepository instructionTypeRepo;
    private final ProofTypeRepository proofTypeRepo;
    private final AppUserRepository userRepo;
    private final AuthService authService;

    public ParametresService(InstructionTypeRepository instructionTypeRepo,
                             ProofTypeRepository proofTypeRepo,
                             AppUserRepository userRepo,
                             AuthService authService) {
        this.instructionTypeRepo = instructionTypeRepo;
        this.proofTypeRepo = proofTypeRepo;
        this.userRepo = userRepo;
        this.authService = authService;
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

    // ── AppUser ───────────────────────────────────────────────────────────────

    public List<AppUser> getAllUsers() {
        return userRepo.findAllByOrderByNomCompletAsc();
    }

    public AppUser createUser(Map<String, Object> body) {
        AppUser u = new AppUser();
        u.setUsername((String) body.get("username"));
        u.setNomComplet((String) body.get("nomComplet"));
        u.setRole(AppUser.Role.valueOf((String) body.get("role")));
        String password = (String) body.getOrDefault("password", "changeme");
        u.setPasswordHash(authService.hashPassword(password));
        return userRepo.save(u);
    }

    public AppUser updateUser(String id, Map<String, Object> body) {
        AppUser u = userRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable: " + id));
        if (body.containsKey("nomComplet"))
            u.setNomComplet((String) body.get("nomComplet"));
        if (body.containsKey("role"))
            u.setRole(AppUser.Role.valueOf((String) body.get("role")));
        if (body.containsKey("actif"))
            u.setActif(Boolean.TRUE.equals(body.get("actif")));
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
