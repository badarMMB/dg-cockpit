package com.dgcockpit.controller;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        try {
            AppUser user = authService.login(body.get("username"), body.get("password"));
            String token = authService.createToken(user);
            Map<String, Object> resp = new HashMap<>();
            resp.put("token", token);
            resp.put("user", toUserDto(user));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        AppUser user = (AppUser) request.getAttribute("currentUser");
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(toUserDto(user));
    }

    @SuppressWarnings("deprecation")
    static Map<String, Object> toUserDto(AppUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nomComplet", u.getNomComplet());
        m.put("role", u.getRole() != null ? u.getRole().name() : null);
        m.put("actif", u.isActif());
        m.put("posteId",      u.getPoste() != null ? u.getPoste().getId()      : null);
        m.put("posteLibelle", u.getPoste() != null ? u.getPoste().getLibelle() : null);
        m.put("hasBureau", u.hasBureau());
        m.put("canSign",   u.hasHabilitation(com.dgcockpit.entity.Poste.Habilitation.CAN_SIGN));
        m.put("managerId",  u.getManager() != null ? u.getManager().getId()        : null);
        m.put("managerNom", u.getManager() != null ? u.getManager().getNomComplet() : null);
        return m;
    }
}
