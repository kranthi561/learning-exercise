package com.instagram.mcp.video;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

// Combines a still image and an audio file into an MP4 suitable for Instagram Reels
// using FFmpeg. Output is a 1080×1080 square video (Instagram supports square Reels).
//
// Requires FFmpeg on the system PATH — install with: brew install ffmpeg
// Caller is responsible for deleting the returned path after use.
@Service
@Slf4j
public class VideoService {

    private static final int CANVAS = 1080;
    private static final int FPS    = 30;

    public Path createFromImageAndAudio(Path image, Path audio, Path outputDir)
            throws IOException, InterruptedException {
        assertFfmpegAvailable();
        Files.createDirectories(outputDir);

        Path output = outputDir.resolve("reel-" + System.currentTimeMillis() + ".mp4");

        // Scale image to fit 1080×1080 with black letterbox, encode H.264 + AAC.
        String vf = "scale=" + CANVAS + ":" + CANVAS
                + ":force_original_aspect_ratio=decrease,"
                + "pad=" + CANVAS + ":" + CANVAS + ":(ow-iw)/2:(oh-ih)/2:black,"
                + "format=yuv420p";

        List<String> cmd = List.of(
                "ffmpeg", "-y",
                "-loop", "1", "-framerate", String.valueOf(FPS),
                "-i", image.toAbsolutePath().toString(),
                "-i", audio.toAbsolutePath().toString(),
                "-vf", vf,
                "-c:v", "libx264", "-preset", "fast", "-crf", "23",
                "-c:a", "aac", "-b:a", "128k",
                "-shortest",
                output.toAbsolutePath().toString());

        log.debug("createFromImageAndAudio: running ffmpeg for {}", image.getFileName());

        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

        String ffmpegLog = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.error("FFmpeg failed (exit={}): {}", exitCode, ffmpegLog);
            throw new IOException("FFmpeg failed with exit code " + exitCode
                    + ". Ensure ffmpeg is installed: brew install ffmpeg");
        }

        log.debug("createFromImageAndAudio: output={}, {} bytes",
                output.getFileName(), Files.size(output));
        return output;
    }

    // Returns the duration of a video file in whole seconds (ceiling), using ffprobe.
    // Required by Instagram's /video_reels?upload_phase=start&clip_length= parameter.
    public int getDurationSeconds(Path videoFile) throws IOException, InterruptedException {
        List<String> cmd = List.of(
                "ffprobe", "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoFile.toAbsolutePath().toString());
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        int exitCode = process.waitFor();
        if (exitCode != 0 || output.isBlank()) {
            log.warn("getDurationSeconds: ffprobe failed for {}, defaulting to 60s", videoFile.getFileName());
            return 60;
        }
        try {
            int secs = (int) Math.ceil(Double.parseDouble(output));
            log.debug("getDurationSeconds: {}={}s", videoFile.getFileName(), secs);
            return secs;
        } catch (NumberFormatException e) {
            log.warn("getDurationSeconds: cannot parse '{}' for {}, defaulting to 60s", output, videoFile.getFileName());
            return 60;
        }
    }

    private void assertFfmpegAvailable() throws IOException {
        try {
            new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking for ffmpeg");
        } catch (IOException e) {
            throw new IOException(
                    "FFmpeg not found on PATH. Install with: brew install ffmpeg", e);
        }
    }
}
