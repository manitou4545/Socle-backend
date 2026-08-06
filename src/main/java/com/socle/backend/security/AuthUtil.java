package com.socle.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthUtil {
    private final TokenStore tokenStore;

    public AuthUtil(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring("Bearer ".length()).trim();
    }

    public Session requireAdmin(String authHeader) {
        Session s = tokenStore.get(extractToken(authHeader));
        if (s == null || s.getRole() != Session.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Accès administrateur requis");
        }
        return s;
    }

    public String requireStudent(String authHeader) {
        Session s = tokenStore.get(extractToken(authHeader));
        if (s == null || s.getRole() != Session.Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connexion étudiante requise");
        }
        return s.getMatricule();
    }

    public String optionalStudent(String authHeader) {
        Session s = tokenStore.get(extractToken(authHeader));
        return (s != null && s.getRole() == Session.Role.STUDENT) ? s.getMatricule() : null;
    }
}
