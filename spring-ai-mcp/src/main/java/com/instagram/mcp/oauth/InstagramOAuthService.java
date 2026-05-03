package com.instagram.mcp.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.instagram.mcp.config.InstagramProperties.InstagramConfig;
import com.instagram.mcp.config.InstagramProperties.InstagramConfig.OAuth;

import lombok.extern.slf4j.Slf4j;

// Implements the "Instagram API with Instagram Login" OAuth flow:
//
//   1. buildAuthorizeUrl()      — construct the URL the browser is redirected to.
//   2. exchangeCodeForToken()   — POST code to api.instagram.com → short-lived token.
//   3. exchangeForLongLived()   — GET to graph.instagram.com    → 60-day token.
//
// The controller wires these three steps together and persists the final
// long-lived token in InstagramTokenStore.
@Service
@Slf4j
public class InstagramOAuthService {

    private static final String AUTHORIZE_BASE = "https://api.instagram.com/oauth/authorize";
    private static final String SHORT_TOKEN_URL = "https://api.instagram.com/oauth/access_token";
    private static final String LONG_TOKEN_BASE = "https://graph.instagram.com/access_token";

    private final RestClient restClient;
    private final InstagramConfig config;

    public InstagramOAuthService(RestClient.Builder restClientBuilder, InstagramConfig config) {
        this.restClient = restClientBuilder.build();
        this.config = config;
    }

    public String buildAuthorizeUrl(String state) {
        OAuth o = oauth();
        String url = AUTHORIZE_BASE
                + "?enable_fb_login=0"
                + "&force_authentication=1"
                + "&client_id=" + enc(o.appId())
                + "&redirect_uri=" + enc(o.redirectUri())
                + "&response_type=code"
                + "&scope=" + enc(o.scope())
                + (state != null && !state.isBlank() ? "&state=" + enc(state) : "");
        log.debug("buildAuthorizeUrl: {}", url);
        return url;
    }

    // Returns short-lived access token + numeric user id.
    public ShortLivedToken exchangeCodeForToken(String code) {
        OAuth o = oauth();
        log.debug("exchangeCodeForToken: code length={}", code == null ? 0 : code.length());
        // Some IG redirects append a stray "#_" fragment to the code value.
        String cleanCode = code == null ? "" : code.replaceAll("#_$", "");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", o.appId());
        form.add("client_secret", o.appSecret());
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", o.redirectUri());
        form.add("code", cleanCode);

        Map<?, ?> response = restClient.post()
                .uri(SHORT_TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new IllegalStateException("Empty response from Instagram short-token endpoint");
        }
        String accessToken = String.valueOf(response.get("access_token"));
        Object userIdRaw = response.get("user_id");
        String userId = userIdRaw == null ? null : String.valueOf(userIdRaw);
        log.info("exchangeCodeForToken: short-lived token obtained for userId={}", userId);
        return new ShortLivedToken(accessToken, userId);
    }

    // Trades the short-lived token for a 60-day long-lived token.
    public LongLivedToken exchangeForLongLived(String shortLivedToken) {
        OAuth o = oauth();
        log.debug("exchangeForLongLived: starting");
        String url = LONG_TOKEN_BASE
                + "?grant_type=ig_exchange_token"
                + "&client_secret=" + enc(o.appSecret())
                + "&access_token=" + enc(shortLivedToken);

        Map<?, ?> response = restClient.get()
                .uri(url)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new IllegalStateException("Empty response from Instagram long-token endpoint");
        }
        String accessToken = String.valueOf(response.get("access_token"));
        Object expiresRaw = response.get("expires_in");
        long expiresIn = expiresRaw instanceof Number n ? n.longValue() : 0L;
        log.info("exchangeForLongLived: long-lived token obtained, expiresInSec={}", expiresIn);
        return new LongLivedToken(accessToken, expiresIn);
    }

    public boolean isConfigured() {
        OAuth o = oauth();
        return o != null
                && notBlank(o.appId())
                && notBlank(o.appSecret())
                && notBlank(o.redirectUri());
    }

    private OAuth oauth() {
        return config.oauth();
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }

    public record ShortLivedToken(String accessToken, String userId) {}
    public record LongLivedToken(String accessToken, long expiresInSeconds) {}
}
