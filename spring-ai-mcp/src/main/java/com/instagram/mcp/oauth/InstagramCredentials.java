package com.instagram.mcp.oauth;

import org.springframework.stereotype.Component;

import com.instagram.mcp.config.InstagramProperties.InstagramConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Single source of truth for the active Instagram credentials at request time.
// Resolves the token + user ID with the priority: OAuth token store first,
// pre-issued config values second. This lets the app boot with no creds and
// pick up a token after the user finishes the OAuth flow — no restart.
@Component
@RequiredArgsConstructor
@Slf4j
public class InstagramCredentials {

    private final InstagramConfig config;
    private final InstagramTokenStore tokenStore;

    public String accessToken() {
        return tokenStore.token().filter(s -> !s.isBlank()).orElse(config.accessToken());
    }

    public String userId() {
        return tokenStore.userId().filter(s -> !s.isBlank()).orElse(config.userId());
    }

    public boolean isConfigured() {
        String t = accessToken();
        String u = userId();
        // log.info("InstagramCredentials: accessToken={}, userId={}", t, u);
        return t != null && !t.isBlank() && u != null && !u.isBlank();
    }

    // True when the live token came from the OAuth flow rather than env config.
    public boolean isFromOAuth() {
        return tokenStore.snapshot().isPresent();
    }
}
