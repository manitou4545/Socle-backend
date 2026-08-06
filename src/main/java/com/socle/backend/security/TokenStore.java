package com.socle.backend.security;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenStore {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String issue(Session session) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, session);
        return token;
    }
    public Session get(String token) {
        if (token == null) return null;
        return sessions.get(token);
    }
    public void revoke(String token) {
        sessions.remove(token);
    }
}
