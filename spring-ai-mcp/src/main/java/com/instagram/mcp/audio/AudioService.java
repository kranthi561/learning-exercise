package com.instagram.mcp.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

// Converts text to speech using OpenAI's TTS API (tts-1 model).
// Writes the resulting MP3 to a caller-supplied directory.
// Caller is responsible for deleting the file after use.
@Service
@Slf4j
public class AudioService {

    private static final String TTS_URL  = "https://api.openai.com/v1/audio/speech";
    private static final String TTS_MODEL = "tts-1";
    private static final int    MAX_CHARS = 4096;

    private final RestClient restClient;
    private final String apiKey;

    public AudioService(RestClient.Builder restClientBuilder,
                        @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    // voice: one of alloy, echo, fable, onyx, nova, shimmer
    public Path generateSpeech(String text, String voice, Path outputDir) throws IOException {
        if (!isEnabled()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured — cannot generate audio.");
        }

        String input = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        log.debug("generateSpeech: voice={}, chars={}", voice, input.length());

        byte[] audioBytes = restClient.post()
                .uri(TTS_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", TTS_MODEL, "input", input, "voice", voice))
                .retrieve()
                .body(byte[].class);

        if (audioBytes == null || audioBytes.length == 0) {
            throw new IOException("Empty audio response from OpenAI TTS");
        }

        Files.createDirectories(outputDir);
        Path audioFile = outputDir.resolve("audio-" + System.currentTimeMillis() + ".mp3");
        Files.write(audioFile, audioBytes);
        log.debug("generateSpeech: wrote {} bytes → {}", audioBytes.length, audioFile.getFileName());
        return audioFile;
    }
}
