package com.dgcockpit.filter;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.entity.Poste;
import com.dgcockpit.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    private static final Set<String> PUBLIC_API_PATHS = Set.of(
        "/api/auth/login",
        "/api/events"
    );

    private static final Set<String> PUBLIC_API_PREFIXES = Set.of(
        "/api/parapheur/audio/",
        // WOPI : Collabora appelle ces endpoints server-to-server avec un
        // access_token en query param, validé par WopiTokenService (pas de JWT).
        "/api/wopi/"
    );

    public AuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        boolean isPublicPrefix = PUBLIC_API_PREFIXES.stream().anyMatch(path::startsWith);
        if (!path.startsWith("/api/") || PUBLIC_API_PATHS.contains(path) || isPublicPrefix || "OPTIONS".equals(method)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        Optional<AppUser> user = authService.validateToken(authHeader.substring(7));
        if (user.isEmpty()) {
            sendUnauthorized(response, "Session expiree ou invalide");
            return;
        }

        AppUser currentUser = user.get();
        request.setAttribute("currentUser", currentUser);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(currentUser, null, authorities(currentUser));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    @SuppressWarnings("deprecation")
    private List<SimpleGrantedAuthority> authorities(AppUser user) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (user.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        }
        if (user.getPoste() != null && user.getPoste().getHabilitations() != null) {
            for (Poste.Habilitation h : user.getPoste().getHabilitations()) {
                authorities.add(new SimpleGrantedAuthority(h.name()));
            }
        } else if (user.getRole() != null) {
            legacyHabilitations(user.getRole()).stream()
                .map(Poste.Habilitation::name)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        }
        if (user.hasBureau()) {
            authorities.add(new SimpleGrantedAuthority("HAS_BUREAU"));
        }
        return authorities;
    }

    @SuppressWarnings("deprecation")
    private List<Poste.Habilitation> legacyHabilitations(AppUser.Role role) {
        return switch (role) {
            case DG -> Arrays.asList(Poste.Habilitation.values());
            case SECRETAIRE -> List.of(
                Poste.Habilitation.CAN_CREATE_INSTRUCTION,
                Poste.Habilitation.CAN_CLOSE,
                Poste.Habilitation.CAN_VIEW_ALL
            );
            case ADMIN_IT -> List.of(
                Poste.Habilitation.CAN_MANAGE_USERS,
                Poste.Habilitation.CAN_MANAGE_TYPES,
                Poste.Habilitation.CAN_VIEW_ALL
            );
            case SUBORDONNE -> List.of();
        };
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
