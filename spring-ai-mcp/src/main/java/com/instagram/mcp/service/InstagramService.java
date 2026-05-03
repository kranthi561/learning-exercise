package com.instagram.mcp.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.instagram.mcp.oauth.InstagramCredentials;

import lombok.extern.slf4j.Slf4j;

// Wraps the Instagram Graph API v21.0 two-step media-publish flow.
// Step 1 — create a media container (returns creation_id).
// Step 2 — publish the container (returns the final media_id).
//
// Reels use the resumable-upload API so the video bytes are pushed directly to
// Instagram's rupload endpoint — no public URL or ngrok required for video.
//
// Credentials are read at call time via InstagramCredentials so the OAuth
// callback can swap a freshly-issued token in without restarting the app.
@Service
@Slf4j
public class InstagramService {

    private static final String GRAPH_API_BASE = "https://graph.instagram.com/v21.0";

    private final RestClient restClient;
    private final InstagramCredentials credentials;

    public InstagramService(RestClient.Builder restClientBuilder, InstagramCredentials credentials) {
        this.restClient = restClientBuilder.baseUrl(GRAPH_API_BASE).build();
        this.credentials = credentials;
    }

    public String postImage(String imageUrl, String caption) {
        log.debug("postImage: imageUrl={}", imageUrl);
        String creationId = createMediaContainer(imageUrl, caption);
        return publishMediaContainer(creationId);
    }

    public String postReel(String videoUrl, String caption) {
        log.debug("postReel: videoUrl={}", videoUrl);
        Map<?, ?> response = restClient.post()
                .uri("/{userId}/media?media_type=REELS&video_url={videoUrl}&caption={caption}&share_to_feed=true&access_token={token}",
                        credentials.userId(), videoUrl, caption, credentials.accessToken())
                .retrieve()
                .body(Map.class);
        if (response == null) throw new IllegalStateException("Empty response from reel container endpoint");
        String creationId = (String) response.get("id");
        log.debug("postReel: creationId={}", creationId);
        waitForContainerFinished(creationId);
        return publishMediaContainer(creationId);
    }

    private String createMediaContainer(String imageUrl, String caption) {
        log.debug("createMediaContainer: imageUrl={}", imageUrl);
        Map<?, ?> response = restClient.post()
                .uri("/{userId}/media?image_url={imageUrl}&caption={caption}&access_token={token}",
                        credentials.userId(), imageUrl, caption, credentials.accessToken())
                .retrieve()
                .body(Map.class);
        if (response == null) throw new IllegalStateException("Empty response from media container endpoint");
        String creationId = (String) response.get("id");
        log.debug("createMediaContainer: creationId={}", creationId);
        return creationId;
    }

    // Polls /{containerId}?fields=status_code every 5 s until FINISHED (max 2 min).
    private void waitForContainerFinished(String containerId) {
        log.info("waitForContainerFinished: polling containerId={}", containerId);
        int maxAttempts = 24; // 24 × 5 s = 2 minutes
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Reel container");
            }
            Map<?, ?> status = restClient.get()
                    .uri("/{containerId}?fields=status_code&access_token={token}",
                            containerId, credentials.accessToken())
                    .retrieve()
                    .body(Map.class);
            String statusCode = status == null ? null : (String) status.get("status_code");
            log.debug("waitForContainerFinished: attempt={}, status={}", attempt, statusCode);
            if ("FINISHED".equals(statusCode)) {
                log.info("waitForContainerFinished: FINISHED after {}s", attempt * 5);
                return;
            }
            if ("ERROR".equals(statusCode) || "EXPIRED".equals(statusCode)) {
                throw new RuntimeException("Reel container processing failed with status: " + statusCode);
            }
        }
        throw new RuntimeException("Timed out (2 min) waiting for Reel container " + containerId);
    }

    private String publishMediaContainer(String creationId) {
        log.debug("publishMediaContainer: creationId={}", creationId);
        Map<?, ?> response = restClient.post()
                .uri("/{userId}/media_publish?creation_id={creationId}&access_token={token}",
                        credentials.userId(), creationId, credentials.accessToken())
                .retrieve()
                .body(Map.class);
        if (response == null) throw new IllegalStateException("Empty response from media_publish endpoint");
        String mediaId = (String) response.get("id");
        log.debug("publishMediaContainer: mediaId={}", mediaId);
        return mediaId;
    }
}
