package app.services;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;

    private static final long EXPIRATION_TIME = 1000 * 60 * 60;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("TRUSTLEND_JWT_SECRET is missing or blank.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "TRUSTLEND_JWT_SECRET must contain at least 32 UTF-8 bytes for security.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public SecretKey getKey() {
        return key;
    }

    public String extractSubject(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String generateToken(String email) {

        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key)
                .compact();
    }
}