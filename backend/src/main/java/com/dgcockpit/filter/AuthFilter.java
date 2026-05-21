package com.dgcockpit.filter;

import com.dgcockpit.entity.AppUser;
import com.dgcockpit.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Component
@Order(1)
public class AuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    // Paths under /api/ that are accessible without authentication
    private static final Set<String> PUBLIC_API_PATHS = Set.of(
        "/api/auth/login",
        "/api/events"
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

        // Pass through: non-API paths, public API paths, CORS preflight
        if (!path.startsWith("/api/") || PUBLIC_API_PATHS.contains(path) || "OPTIONS".equals(method)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Non authentifié");
            return;
        }

        Optional<AppUser> user = authService.validateToken(authHeader.substring(7));
        if (user.isEmpty()) {
            sendUnauthorized(response, "Session expirée ou invalide");
            return;
        }

        request.setAttribute("currentUser", user.get());
        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
