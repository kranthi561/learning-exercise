package com.instagram.mcp.watcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.instagram.mcp.config.InstagramProperties.InstagramConfig;
import com.instagram.mcp.oauth.InstagramCredentials;
import com.instagram.mcp.upload.InstagramUploadService;
import com.instagram.mcp.upload.InstagramUploadService.Result;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Background watcher that polls the configured source-folder. For each new
// image it generates a caption with Spring AI, posts to Instagram, then
// moves the file into destination-folder.
//
// A single-threaded guard prevents overlapping scans if a previous run is
// still in progress (e.g. slow IG API call).
//
// When no Instagram credentials are present (token not yet issued via OAuth
// and no env-var token), the scan exits early without iterating files. A
// "missing credentials" warning is rate-limited so the log isn't spammed
// every poll while the operator finishes the OAuth flow.
@Component
@RequiredArgsConstructor
@Slf4j
public class InstagramFolderWatcher {

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".heic", ".heif");

    // Don't repeat the "no credentials" warning more often than this.
    private static final long CREDS_WARN_INTERVAL_MS = 60_000L;

    private final InstagramConfig config;
    private final InstagramCredentials credentials;
    private final InstagramUploadService uploadService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastCredsWarnAt = new AtomicLong(0L);

    @PostConstruct
    void init() {
        log.info("InstagramFolderWatcher: enabled={}, source={}, destination={}, pollMs={}",
                config.watcher().enabled(),
                config.sourceFolder(),
                config.destinationFolder(),
                config.watcher().pollIntervalMs());
        ensureFoldersExist();
    }

    @Scheduled(
            fixedDelayString = "${app.instagram.watcher.poll-interval-ms:30000}",
            initialDelayString = "${app.instagram.watcher.initial-delay-ms:5000}")
    public void scanAndUpload() {
        if (!config.watcher().enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.debug("scanAndUpload: previous scan still in progress, skipping");
            return;
        }
        try {
            doScan();
        } finally {
            running.set(false);
        }
    }

    private void doScan() {
        // Short-circuit BEFORE listing files: if no token is available there's
        // no point trying to upload anything. This keeps the log clean while
        // the operator finishes /instagram/oauth/login.
        if (!credentials.isConfigured()) {
            warnNoCredentialsThrottled();
            return;
        }

        Path source = Paths.get(config.sourceFolder());
        if (!Files.isDirectory(source)) {
            log.warn("scanAndUpload: source folder does not exist: {}", source.toAbsolutePath());
            return;
        }

        long minAgeMs = config.watcher().minFileAgeMs();
        long now = System.currentTimeMillis();

        List<Path> toUpload;
        try (Stream<Path> stream = Files.list(source)) {
            toUpload = stream
                    .filter(Files::isRegularFile)
                    .filter(InstagramFolderWatcher::isSupportedImage)
                    .filter(p -> isStable(p, now, minAgeMs))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error("scanAndUpload: failed to list source folder {}", source, e);
            return;
        }

        if (toUpload.isEmpty()) {
            log.debug("scanAndUpload: no new images in {}", source.toAbsolutePath());
            return;
        }
        log.info("scanAndUpload: found {} image(s) to upload", toUpload.size());

        for (Path image : toUpload) {
            Result result = uploadService.uploadAndMove(image, null);
            if (result.success()) {
                log.info("scanAndUpload: uploaded {} (mediaId={})", result.filename(), result.mediaId());
            } else {
                log.error("scanAndUpload: failed {} — {}", result.filename(), result.error());
            }
        }
    }

    private void warnNoCredentialsThrottled() {
        long now = System.currentTimeMillis();
        long last = lastCredsWarnAt.get();
        if (now - last < CREDS_WARN_INTERVAL_MS) {
            log.debug("scanAndUpload: no Instagram credentials yet, scan skipped");
            return;
        }
        if (lastCredsWarnAt.compareAndSet(last, now)) {
            log.warn("scanAndUpload: no Instagram credentials yet — scan paused. "
                    + "Visit {}/instagram/oauth/login (or set INSTAGRAM_ACCESS_TOKEN+INSTAGRAM_USER_ID).",
                    trimSlash(config.publicBaseUrl()));
        }
    }

    private void ensureFoldersExist() {
        try {
            Files.createDirectories(Paths.get(config.sourceFolder()));
            Files.createDirectories(Paths.get(config.destinationFolder()));
        } catch (IOException e) {
            log.warn("ensureFoldersExist: could not create folders: {}", e.getMessage());
        }
    }

    private static boolean isSupportedImage(Path path) {
        String name = path.getFileName().toString();
        // Skip internal temp files produced by WatermarkService and ImageConversionService.
        if (name.startsWith(com.instagram.mcp.upload.WatermarkService.TEMP_PREFIX)
                || name.startsWith(com.instagram.mcp.upload.ImageConversionService.CONV_PREFIX)) {
            return false;
        }
        String lower = name.toLowerCase();
        int dot = lower.lastIndexOf('.');
        return dot >= 0 && SUPPORTED_EXTENSIONS.contains(lower.substring(dot));
    }

    // Skip files whose lastModified is too recent — they may still be writing.
    private static boolean isStable(Path path, long nowMs, long minAgeMs) {
        try {
            long age = nowMs - Files.getLastModifiedTime(path).toMillis();
            return age >= minAgeMs;
        } catch (IOException e) {
            return false;
        }
    }

    private static String trimSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }
}
