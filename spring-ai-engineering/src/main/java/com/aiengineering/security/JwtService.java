package com.aiengineering.security;

import com.aiengineering.config.JwtProperties;
import com.aiengineering.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtService {

    private final JwtProperties properties;

    // Pre-built signing key held in memory — avoids re-deriving it on every token operation.
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        log.debug("JwtService: initialising with expirationSeconds={}", properties.expirationSeconds());
        this.properties = properties;
        byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        // HS256 requires at least a 256-bit (32-byte) key.
        // Fail fast at startup rather than silently accepting a weak secret.
        if (bytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 32 bytes (256-bit) for HS256");
        }
        // Keys.hmacShaKeyFor wraps the raw bytes into a JJWT SecretKey for HS256 signing.
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    // Creates a signed JWT for the given user.
    // The subject is the user's numeric ID — keeps tokens small and avoids
    // exposing email in the token payload by default.
    public String createToken(User user) {
        log.debug("createToken: userId={}, email={}", user.getId(), user.getEmail());
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.expirationSeconds());
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))  // stored as JWT "sub" claim
                .claim("email", user.getEmail())        // custom claim for convenience in filters
                .issuedAt(Date.from(now))               // "iat" standard claim
                .expiration(Date.from(exp))             // "exp" standard claim — token auto-expires
                .signWith(key)                          // HMAC-SHA256 signature using our secret key
                .compact();                             // serialises to the Base64URL.Base64URL.Base64URL string
    }

    // Parses and validates the token signature and expiry.
    // Throws JwtException (caught by JwtAuthenticationFilter) if the token is
    // expired, tampered with, or signed with a different key.
    public Claims parseClaims(String token) {
        log.debug("parseClaims: parsing token");
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
