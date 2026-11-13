package com.chatbot.backend.security;

import com.chatbot.backend.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * Création et vérification des tokens JWT (HMAC-SHA256).
 * Le secret vient de la variable d'environnement JWT_SECRET.
 */
@Service
public class JwtService {

    private static final String ISSUER = "smartdesk";
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${app.jwt.secret:}")
    private String secret;

    @Value("${app.jwt.expiration-ms:28800000}")
    private long expirationMs;

    private SecretKey key;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank() || secret.length() < MIN_SECRET_LENGTH
                || secret.contains("CHANGE_ME")) {
            throw new IllegalStateException(
                    "JWT_SECRET manquant ou trop court (minimum " + MIN_SECRET_LENGTH
                            + " caractères). Lancez scripts\\setup.ps1 ou renseignez JWT_SECRET dans le fichier .env.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Retourne le nom d'utilisateur si le token est valide (signature, émetteur, expiration). */
    public Optional<String> validateAndGetUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
