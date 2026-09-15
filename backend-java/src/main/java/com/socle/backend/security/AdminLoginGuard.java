package com.socle.backend.security;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Anti brute-force pour la connexion admin — appliqué CÔTÉ SERVEUR,
 * donc impossible à contourner en modifiant le code du navigateur
 * (contrairement à l'équivalent front-end).
 */
@Component
public class AdminLoginGuard {
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MS = 60_000;

    private final AtomicInteger failCount = new AtomicInteger(0);
    private volatile long lockUntil = 0;

    public boolean isLocked() {
        return System.currentTimeMillis() < lockUntil;
    }
    public long remainingLockSeconds() {
        return Math.max(0, (lockUntil - System.currentTimeMillis()) / 1000);
    }
    public void recordSuccess() {
        failCount.set(0);
    }
    public void recordFailure() {
        int count = failCount.incrementAndGet();
        if (count >= MAX_ATTEMPTS) {
            lockUntil = System.currentTimeMillis() + LOCK_DURATION_MS;
            failCount.set(0);
        }
    }
}
