package com.instagram.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Binds app.instagram.* from application.yml into an immutable record.
//
// Two ways to authenticate:
//   1. Pre-issued long-lived token  →  set access-token + user-id directly.
//   2. OAuth flow                   →  set oauth.app-id + app-secret, then
//      have the user visit /instagram/oauth/login. The exchanged token is
//      kept in the in-memory token store and used for subsequent posts.
@Configuration
@EnableConfigurationProperties(InstagramProperties.InstagramConfig.class)
public class InstagramProperties {

    @ConfigurationProperties(prefix = "app.instagram")
    public record InstagramConfig(
            String accessToken,
            String userId,
            String publicBaseUrl,
            String sourceFolder,
            String destinationFolder,
            String reelTempFolder,
            Watcher watcher,
            OAuth oauth,
            Reel reel) {

        // Background-watcher tuning.
        public record Watcher(
                boolean enabled,
                long pollIntervalMs,
                long initialDelayMs,
                long minFileAgeMs) {
        }

        // OAuth (Instagram API with Instagram Login flow).
        // redirectUri must be registered in the Meta App dashboard exactly.
        public record OAuth(
                String appName,
                String appId,
                String appSecret,
                String redirectUri,
                String scope) {
        }

        // Reel posting — when enabled, images are turned into short videos with
        // AI-narrated captions and posted as Instagram Reels instead of photo posts.
        // Requires OPENAI_API_KEY and ffmpeg on PATH.
        public record Reel(
                boolean enabled,
                String voice) {
        }
    }
}
