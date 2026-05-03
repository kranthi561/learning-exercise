package com.instagram.mcp.web.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.mcp.config.InstagramProperties.InstagramConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Serves images from the configured source folder over HTTP so the Instagram
// Graph API can fetch them via a public URL.
// For local dev, tunnel this server with ngrok so the URL is publicly reachable.
@RestController
@RequestMapping("/instagram/images")
@RequiredArgsConstructor
@Slf4j
public class InstagramImageController {

    private final InstagramConfig instagramConfig;

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serveImage(@PathVariable String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        Path imagePath = Paths.get(instagramConfig.sourceFolder()).resolve(filename).normalize();
        if (!Files.exists(imagePath) || !Files.isRegularFile(imagePath)) {
            return ResponseEntity.notFound().build();
        }
        log.debug("serveImage: serving file={}", imagePath);
        return ResponseEntity.ok()
                .contentType(resolveMediaType(filename))
                .body(new FileSystemResource(imagePath));
    }

    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        return MediaType.IMAGE_JPEG;
    }
}
