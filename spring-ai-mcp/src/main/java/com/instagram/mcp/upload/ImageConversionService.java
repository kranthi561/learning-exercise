package com.instagram.mcp.upload;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

// Ensures an image can be decoded by both Java ImageIO (watermark) and the
// OpenAI Vision API (caption generation). iPhone HEIC/HEIF files are the
// primary case — they may arrive with a .jpeg extension but contain HEIC bytes
// that Java ImageIO and OpenAI both reject.
//
// Detection uses magic bytes (bytes 4-7 == "ftyp") so misnamed files are caught
// regardless of their extension.
//
// HEIC is converted with heif-convert (libheif-examples). The Docker image uses
// eclipse-temurin:21-jre-noble (Ubuntu 24.04) which ships libheif 1.17.6 —
// Ubuntu 22.04's libheif 1.12 failed on modern iPhone HEIC files with
// "Metadata not correctly assigned to image". Other unreadable formats fall back
// to ffmpeg.
//
// Converted files are written to the same directory with a "_conv_" prefix.
// Callers must delete them after use. Use isConverted() to check whether a
// temp file was created.
@Service
@Slf4j
public class ImageConversionService {

    public static final String CONV_PREFIX = "_conv_";

    private static final Set<String> HEIC_EXTENSIONS = Set.of(".heic", ".heif");

    public Path toJpegIfNeeded(Path source) throws IOException {
        // Magic-byte check first — catches HEIC files saved with wrong extensions.
        if (isHeicByMagicBytes(source)) {
            log.debug("toJpegIfNeeded: HEIC magic bytes detected in {}", source.getFileName());
            return convertHeicToJpeg(source);
        }

        // Extension check as a second signal (magic bytes may be unreadable on tiny files).
        if (HEIC_EXTENSIONS.contains(extensionOf(source))) {
            log.debug("toJpegIfNeeded: HEIC extension — {}", source.getFileName());
            return convertHeicToJpeg(source);
        }

        // Try ImageIO; if it returns null the format is unsupported, fall back to ffmpeg.
        try {
            BufferedImage img = ImageIO.read(source.toFile());
            if (img != null) return source;
        } catch (IOException ignored) { }

        log.debug("toJpegIfNeeded: ImageIO cannot read {}, trying ffmpeg", source.getFileName());
        return convertWithFfmpeg(source);
    }

    public static boolean isConverted(Path original, Path working) {
        return !working.equals(original);
    }

    // HEIC/HEIF uses the ISO Base Media File Format (ISOBMFF) container.
    // Bytes 4-7 are the box type "ftyp"; bytes 8-11 are the major brand.
    // Common HEIC brands: heic, heis, hevc, hevx, mif1, msf1.
    private static boolean isHeicByMagicBytes(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            byte[] header = is.readNBytes(12);
            if (header.length < 12) return false;
            String box = new String(header, 4, 4, StandardCharsets.US_ASCII);
            if (!"ftyp".equals(box)) return false;
            String brand = new String(header, 8, 4, StandardCharsets.US_ASCII);
            return brand.startsWith("hei") || brand.startsWith("hev")
                    || brand.equals("mif1") || brand.equals("msf1");
        } catch (IOException e) {
            return false;
        }
    }

    private Path convertHeicToJpeg(Path source) throws IOException {
        Path dest = destPath(source);
        runProcess(
                List.of("heif-convert",
                        source.toAbsolutePath().toString(),
                        dest.toAbsolutePath().toString()),
                source, "heif-convert");
        return dest;
    }

    private Path convertWithFfmpeg(Path source) throws IOException {
        Path dest = destPath(source);
        runProcess(
                List.of("ffmpeg", "-y",
                        "-i", source.toAbsolutePath().toString(),
                        dest.toAbsolutePath().toString()),
                source, "ffmpeg");
        return dest;
    }

    private void runProcess(List<String> cmd, Path source, String tool) throws IOException {
        log.info("toJpegIfNeeded: converting {} with {}", source.getFileName(), tool);
        log.debug("toJpegIfNeeded: cmd={}", cmd);
        log.debug("toJpegIfNeeded: source={}", source);
        log.debug("toJpegIfNeeded: destPath={}", destPath(source));
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during image conversion");
        }
        if (exitCode != 0) {
            log.error("{} failed (exit={}) for {}: {}", tool, exitCode, source.getFileName(), output);
            throw new IOException("Cannot convert " + source.getFileName()
                    + " using " + tool + " (exit=" + exitCode + ")");
        }
        log.debug("toJpegIfNeeded: {} bytes written to {}", Files.size(destPath(source)), destPath(source).getFileName());
    }

    private Path destPath(Path source) {
        return source.getParent().resolve(CONV_PREFIX + stemOf(source) + ".jpg");
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private static String stemOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
