package com.instagram.mcp.upload;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.instagram.mcp.audio.AudioService;
import com.instagram.mcp.caption.CaptionService;
import com.instagram.mcp.config.InstagramProperties.InstagramConfig;
import com.instagram.mcp.oauth.InstagramCredentials;
import com.instagram.mcp.service.InstagramService;
import com.instagram.mcp.video.VideoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Orchestrates the full per-image workflow used by both the watcher and the
// MCP postImageToInstagram tool:
//   1. Generate caption (AI or provided/fallback).
//   2. Build a publicly fetchable image URL.
//   3. Call the Instagram Graph API to publish.
//   4. Move the source file into destination-folder so it isn't reposted.
//
// Centralising this logic guarantees the watcher and manual tool behave
// identically on success and failure.
@Service
@RequiredArgsConstructor
@Slf4j
public class InstagramUploadService {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final InstagramConfig config;
    private final InstagramCredentials credentials;
    private final InstagramService instagramService;
    private final CaptionService captionService;
    private final WatermarkService watermarkService;
    private final ImageConversionService conversionService;
    private final AudioService audioService;
    private final VideoService videoService;

    public Result uploadAndMove(Path sourceImage, String overrideCaption) {
        String filename = sourceImage.getFileName().toString();
        log.debug("uploadAndMove: filename={}", filename);

        if (!Files.isRegularFile(sourceImage)) {
            return Result.failure(filename, null, "File not found: " + sourceImage);
        }
        if (!credentials.isConfigured()) {
            return Result.failure(filename, null,
                    "Instagram credentials not configured. Either set INSTAGRAM_ACCESS_TOKEN+INSTAGRAM_USER_ID, "
                    + "or run the OAuth flow at /instagram/oauth/login.");
        }
        if (isLocalUrl(config.publicBaseUrl())) {
            return Result.failure(filename, null,
                    "PUBLIC_BASE_URL is set to a local address (" + config.publicBaseUrl() + "). "
                    + "Instagram's API cannot fetch images from localhost or private IPs. "
                    + "Run: ngrok http 8081  — then set PUBLIC_BASE_URL to the ngrok HTTPS URL in your .env.");
        }

        // 0. Convert HEIC/unsupported formats (e.g. iPhone photos) to JPEG.
        //    workingImage == sourceImage when no conversion is needed.
        Path workingImage = sourceImage;
        try {
            workingImage = conversionService.toJpegIfNeeded(sourceImage);
            if (ImageConversionService.isConverted(sourceImage, workingImage)) {
                log.info("uploadAndMove: converted {} → {}", filename, workingImage.getFileName());
            }
        } catch (IOException e) {
            return Result.failure(filename, null,
                    "Image format not supported and conversion failed: " + e.getMessage()
                    + ". Ensure ffmpeg is installed (brew install ffmpeg).");
        }

        Path converted = ImageConversionService.isConverted(sourceImage, workingImage) ? workingImage : null;
        Path watermarked = null;
        String caption;

        try {
            // 1. Caption — generated from the decoded workingImage; always ends with brand tag.
            if (overrideCaption != null && !overrideCaption.isBlank()) {
                caption = overrideCaption;
            } else {
                caption = captionService.generateCaption(workingImage);
            }
            caption = withBrandTag(caption);
            log.debug("uploadAndMove: caption={}", caption);

            // 2. Watermark applied to workingImage; original/converted untouched.
            String uploadFilename = workingImage.getFileName().toString();
            try {
                watermarked = watermarkService.applyWatermark(workingImage);
                uploadFilename = watermarked.getFileName().toString();
                log.debug("uploadAndMove: watermark applied → {}", uploadFilename);
            } catch (Exception e) {
                log.warn("uploadAndMove: watermark failed for {}, continuing without: {}", filename, e.getMessage());
            }

            // 3. Build public image URL — the IG Graph API fetches this URL.
            String encodedName = URLEncoder.encode(uploadFilename, StandardCharsets.UTF_8).replace("+", "%20");
            String publicImageUrl = trimTrailingSlash(config.publicBaseUrl()) + "/instagram/images/" + encodedName;
            log.debug("uploadAndMove: publicImageUrl={}", publicImageUrl);

            // 4. Publish to Instagram.
            String mediaId;
            try {
                if (config.reel() != null && config.reel().enabled()) {
                    mediaId = tryPublishAsReel(watermarked != null ? watermarked : workingImage, caption);
                } else {
                    mediaId = instagramService.postImage(publicImageUrl, caption);
                }
            } catch (Exception e) {
                log.error("uploadAndMove: Instagram publish failed for {}", filename, e);
                return Result.failure(filename, caption, e.getMessage());
            }

            // 5. Move original source file to destination.
            Path moved;
            try {
                moved = moveToDestination(sourceImage);
            } catch (IOException e) {
                log.error("uploadAndMove: published mediaId={} but move failed for {}", mediaId, filename, e);
                return new Result(true, mediaId, filename, caption, null,
                        "Posted to Instagram but could not move file: " + e.getMessage());
            }

            log.info("uploadAndMove: {} → mediaId={}, moved to {}", filename, mediaId, moved);
            return new Result(true, mediaId, filename, caption, moved.toAbsolutePath().toString(), null);

        } finally {
            deleteQuietly(watermarked);
            deleteQuietly(converted);
        }
    }

    private Path moveToDestination(Path sourceImage) throws IOException {
        Path destDir = Paths.get(config.destinationFolder());
        Files.createDirectories(destDir);
        String filename = sourceImage.getFileName().toString();
        Path target = destDir.resolve(filename);
        if (Files.exists(target)) {
            int dot = filename.lastIndexOf('.');
            String stem = dot >= 0 ? filename.substring(0, dot) : filename;
            String ext  = dot >= 0 ? filename.substring(dot)    : "";
            target = destDir.resolve(stem + "-" + LocalDateTime.now().format(STAMP) + ext);
        }
        return Files.move(sourceImage, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String trimTrailingSlash(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // best-effort cleanup — log but don't fail the upload result
        }
    }

    // Generates TTS audio from caption, combines with image into MP4, posts as Reel.
    // Falls back to a regular photo post if audio or FFmpeg fails.
    private String tryPublishAsReel(Path image, String caption) throws Exception {
        Path reelTempDir = Paths.get(config.reelTempFolder());
        String voice = config.reel().voice() != null ? config.reel().voice() : "nova";

        Path audioFile = null;
        Path videoFile = null;
        try {
            audioFile = audioService.generateSpeech(stripForSpeech(caption), voice, reelTempDir);
            videoFile = videoService.createFromImageAndAudio(image, audioFile, reelTempDir);
            deleteQuietly(audioFile);
            audioFile = null;

            String encodedName = URLEncoder.encode(videoFile.getFileName().toString(), StandardCharsets.UTF_8);
            String videoUrl = trimTrailingSlash(config.publicBaseUrl()) + "/instagram/videos/" + encodedName;
            log.info("tryPublishAsReel: videoUrl={}", videoUrl);
            return instagramService.postReel(videoUrl, caption);
        } catch (Exception e) {
            log.error("tryPublishAsReel: failed ({}), falling back to photo post", e.getMessage());
            String encodedName = URLEncoder.encode(image.getFileName().toString(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String photoUrl = trimTrailingSlash(config.publicBaseUrl()) + "/instagram/images/" + encodedName;
            return instagramService.postImage(photoUrl, caption);
        } finally {
            deleteQuietly(audioFile);
            deleteQuietly(videoFile);
        }
    }

    private static String stripForSpeech(String caption) {
        return caption.replaceAll("#\\S+", "").replaceAll("\\s+", " ").trim();
    }

    private static String withBrandTag(String caption) {
        String tag = CaptionService.DEFAULT_HASHTAGS.split(" ")[0]; // #naturesrawclicks
        if (caption == null || caption.isBlank()) return CaptionService.DEFAULT_HASHTAGS;
        if (caption.toLowerCase().contains(tag.toLowerCase())) return caption;
        return caption + "\n\n" + tag;
    }

    private static boolean isLocalUrl(String url) {
        if (url == null || url.isBlank()) return true;
        String lower = url.toLowerCase();
        return lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("0.0.0.0");
    }

    public record Result(
            boolean success,
            String mediaId,
            String filename,
            String caption,
            String movedTo,
            String error) {

        static Result failure(String filename, String caption, String error) {
            return new Result(false, null, filename, caption, null, error);
        }
    }
}
