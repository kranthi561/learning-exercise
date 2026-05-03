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

// Serves temporary Reel video files so the Instagram Graph API can download them.
// Files are written by VideoService into reel-temp-folder and deleted after
// Instagram's container status reaches FINISHED.
@RestController
@RequestMapping("/instagram/videos")
@RequiredArgsConstructor
@Slf4j
public class InstagramVideoController {

    private final InstagramConfig instagramConfig;

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serveVideo(@PathVariable String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        Path videoPath = Paths.get(instagramConfig.reelTempFolder())
                .resolve(filename).normalize();
        if (!Files.exists(videoPath) || !Files.isRegularFile(videoPath)) {
            log.warn("serveVideo: not found — {}", filename);
            return ResponseEntity.notFound().build();
        }
        log.debug("serveVideo: {}", videoPath.getFileName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(new FileSystemResource(videoPath));
    }
}
