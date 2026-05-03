package com.instagram.mcp.oauth;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.mcp.config.InstagramProperties.InstagramConfig;
import com.instagram.mcp.oauth.InstagramOAuthService.LongLivedToken;
import com.instagram.mcp.oauth.InstagramOAuthService.ShortLivedToken;
import com.instagram.mcp.oauth.InstagramTokenStore.TokenSnapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// HTTP entry points for the Instagram OAuth flow.
//
//   GET /instagram/oauth/login    -> 302 redirect to Instagram authorize URL
//   GET /instagram/oauth/callback -> handles ?code=, exchanges for long-lived token, stores it
//   GET /instagram/oauth/status   -> JSON status (whether a token is loaded)
//   POST /instagram/oauth/logout  -> clears the in-memory token
@RestController
@RequestMapping("/instagram/oauth")
@RequiredArgsConstructor
@Slf4j
public class InstagramOAuthController {

    private final InstagramConfig config;
    private final InstagramOAuthService oauthService;
    private final InstagramTokenStore tokenStore;

    @GetMapping("/login")
    public ResponseEntity<?> login() {
        if (!oauthService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("OAuth is not configured. Set INSTAGRAM_APP_ID, INSTAGRAM_APP_SECRET, INSTAGRAM_REDIRECT_URI.");
        }
        // The state value isn't validated server-side here (no session) — it's
        // only echoed back so a vigilant operator can spot tampered redirects.
        String state = UUID.randomUUID().toString();
        String authorizeUrl = oauthService.buildAuthorizeUrl(state);
        log.info("oauth/login: redirecting to {}", authorizeUrl);
        try {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(authorizeUrl)).build();
        } catch (Exception e) {
            log.error("oauth/login: error redirecting to Instagram", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Error redirecting to Instagram: " + e.getMessage());
        }
        

    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription,
            @RequestParam(value = "state", required = false) String state) {

        if (error != null) {
            log.warn("oauth/callback: error={} desc={}", error, errorDescription);
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                    .body(htmlError("Instagram returned an error: " + error
                            + (errorDescription != null ? " — " + errorDescription : "")));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                    .body(htmlError("Missing 'code' query parameter."));
        }

        try {
            ShortLivedToken shortLived = oauthService.exchangeCodeForToken(code);
            LongLivedToken longLived = oauthService.exchangeForLongLived(shortLived.accessToken());
            tokenStore.store(longLived.accessToken(), shortLived.userId(), longLived.expiresInSeconds());
            log.info("oauth/callback: success, userId={} state={}", shortLived.userId(), state);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                    .body(htmlSuccess(shortLived.userId(), longLived.expiresInSeconds()));
        } catch (Exception e) {
            log.error("oauth/callback: token exchange failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML)
                    .body(htmlError("Token exchange failed: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Optional<TokenSnapshot> snap = tokenStore.snapshot();
        return Map.of(
                "appName", nullToEmpty(config.oauth() == null ? null : config.oauth().appName()),
                "redirectUri", nullToEmpty(config.oauth() == null ? null : config.oauth().redirectUri()),
                "tokenLoaded", snap.isPresent(),
                "userId", snap.map(TokenSnapshot::userId).orElse(""),
                "expiresAt", snap.map(TokenSnapshot::expiresAt).map(Object::toString).orElse(""),
                "secondsUntilExpiry", snap.map(s -> s.expiresAt() == null ? -1L
                        : Math.max(0, s.expiresAt().getEpochSecond() - Instant.now().getEpochSecond())).orElse(-1L));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        tokenStore.clear();
        return ResponseEntity.ok("Token cleared.");
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String htmlSuccess(String userId, long expiresIn) {
        return "<!doctype html><html><body style=\"font-family:sans-serif;max-width:560px;margin:40px auto\">"
                + "<h2>Instagram connected</h2>"
                + "<p>Long-lived access token stored in memory.</p>"
                + "<ul><li>User ID: <code>" + escape(userId) + "</code></li>"
                + "<li>Expires in: " + expiresIn + " seconds (~" + (expiresIn / 86400) + " days)</li></ul>"
                + "<p>You can close this tab.</p></body></html>";
    }

    private static String htmlError(String message) {
        return "<!doctype html><html><body style=\"font-family:sans-serif;max-width:560px;margin:40px auto\">"
                + "<h2>Instagram OAuth failed</h2><p>" + escape(message) + "</p></body></html>";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
