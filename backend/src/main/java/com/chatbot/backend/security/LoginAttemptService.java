package com.chatbot.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection contre le brute-force : après N échecs de connexion pour un
 * même couple (nom d'utilisateur, adresse IP), la connexion est bloquée
 * pendant quelques minutes.
 */
@Service
public class LoginAttemptService {

    private record Attempts(int count, Instant firstFailure, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(
            @Value("${app.security.max-login-attempts:5}") int maxAttempts,
            @Value("${app.security.login-lock-minutes:15}") int lockMinutes) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockDuration = Duration.ofMinutes(Math.max(1, lockMinutes));
    }

    private static String key(String username, String ip) {
        return (username == null ? "" : username.trim().toLowerCase(Locale.ROOT)) + "|" + ip;
    }

    public boolean isBlocked(String username, String ip) {
        Attempts a = attempts.get(key(username, ip));
        return a != null && a.lockedUntil() != null && Instant.now().isBefore(a.lockedUntil());
    }

    public void loginFailed(String username, String ip) {
        failed(username, ip, maxAttempts);
    }

    /** Échec d'une autre opération sensible (ex. vérification d'identité à l'inscription). */
    public void failed(String key, String ip, int max) {
        int limite = Math.max(1, max);
        Instant now = Instant.now();
        attempts.compute(key(key, ip), (k, a) -> {
            // Fenêtre de comptage = durée du blocage
            if (a == null || a.firstFailure().plus(lockDuration).isBefore(now)) {
                a = new Attempts(0, now, null);
            }
            int count = a.count() + 1;
            Instant lockedUntil = count >= limite ? now.plus(lockDuration) : a.lockedUntil();
            return new Attempts(count, a.firstFailure(), lockedUntil);
        });
        // Nettoyage simple pour éviter une croissance infinie de la map
        if (attempts.size() > 10_000) {
            attempts.entrySet().removeIf(e -> e.getValue().firstFailure().plus(lockDuration).isBefore(now));
        }
    }

    public void loginSucceeded(String username, String ip) {
        attempts.remove(key(username, ip));
    }

    public long lockMinutes() {
        return lockDuration.toMinutes();
    }
}
