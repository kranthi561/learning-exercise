package com.instagram.mcp.oauth;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// In-memory holder for the long-lived Instagram access token + user ID
// obtained via the OAuth callback. Cleared on application restart.
//
// Concurrency: a single AtomicReference holds an immutable snapshot so
// reads are lock-free and any update is atomic.
@Component
@Slf4j
public class InstagramTokenStore {

    private final AtomicReference<TokenSnapshot> ref = new AtomicReference<>();

    public void store(String accessToken, String userId, long expiresInSeconds) {
        Instant expiresAt = expiresInSeconds > 0
                ? Instant.now().plusSeconds(expiresInSeconds)
                : null;
        ref.set(new TokenSnapshot(accessToken, userId, expiresAt));
        log.info("InstagramTokenStore: token stored for userId={}, expiresAt={}", userId, expiresAt);
    }

    public void clear() {
        ref.set(null);
        log.info("InstagramTokenStore: cleared");
    }

    public Optional<String> token() {
        TokenSnapshot s = ref.get();
        return Optional.ofNullable(s).map(TokenSnapshot::accessToken);
    }

    public Optional<String> userId() {
        TokenSnapshot s = ref.get();
        return Optional.ofNullable(s).map(TokenSnapshot::userId);
    }

    public Optional<TokenSnapshot> snapshot() {
        return Optional.ofNullable(ref.get());
    }

    // Immutable snapshot of the active OAuth token.
    public record TokenSnapshot(String accessToken, String userId, Instant expiresAt) {
    }
}
