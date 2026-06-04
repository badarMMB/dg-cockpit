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
    private final AppUserRepository userRepo;
    private final PosteRepository posteRepo;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public ParametresService(InstructionTypeRepository instructionTypeRepo,
                             AppUserRepository userRepo,
                             PosteRepository posteRepo,
                             AuthService authService,
                             ObjectMapper objectMapper) {
        this.instructionTypeRepo = instructionTypeRepo;
        this.userRepo            = userRepo;
        this.posteRepo           = posteRepo;
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
        if (body.containsKey("typeInstruction")) {
            String ti = (String) body.get("typeInstruction");
            t.setTypeInstruction(ti != null
                ? InstructionType.TypeInstruction.valueOf(ti)
                : InstructionType.TypeInstruction.LIBRE);
        }
        if (body.containsKey("typeDocumentAttenduId"))
            t.setTypeDocumentAttenduId((String) body.get("typeDocumentAttenduId"));
        if (body.containsKey("actif"))
            t.setActif(Boolean.TRUE.equals(body.get("actif")));
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
        if (body.containsKey("managerId") && body.get("managerId") != null) {
            userRepo.findById((String) body.get("managerId")).ifPresent(u::setManager);
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
        if (body.containsKey("managerId")) {
            String managerId = (String) body.get("managerId");
            u.setManager(managerId != null ? userRepo.findById(managerId).orElse(null) : null);
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
