package com.instagram.mcp.caption;

import java.nio.file.Path;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import lombok.extern.slf4j.Slf4j;

// Generates Instagram captions for an image using a Spring AI ChatClient
// with multimodal input (vision). Falls back to a filename-derived caption
// when no API key is configured or the call fails — so the watcher pipeline
// keeps working even without AI credentials.
//
// IMPORTANT: ChatClient.Builder is injected with @Lazy and the ChatClient
// itself is built on first use. Why?
//
// Spring AI's ToolCallingAutoConfiguration wires every ToolCallbackProvider
// in the context (including our MCP one) into the auto-configured chat model.
// Our MCP tool calls InstagramUploadService -> CaptionService, so resolving
// the chat client at CaptionService construction time creates a cycle:
//
//   captionService -> chatClientBuilder -> openAiChatModel
//     -> toolCallingManager -> toolCallbackResolver
//     -> mcpToolCallbackProvider -> instagramMcpTools
//     -> instagramUploadService -> captionService
//
// Lazy injection breaks the cycle: Spring wires CaptionService with a proxy,
// finishes the rest of the bean graph, and only resolves the real ChatClient
// the first time generateCaption() actually needs it.
@Service
@Slf4j
public class CaptionService {

    public static final String DEFAULT_HASHTAGS =
            "#naturesrawclicks #nature #naturephotography #wildlife #photography";

    private static final String CAPTION_PROMPT = """
            You are writing the caption for an Instagram post for the account @naturesrawclicks.
            Requirements:
            - One short, engaging caption (1-2 sentences, under 200 characters).
            - Friendly, natural tone — no preamble like "Caption:" or surrounding quotes.
            - Include 5 to 8 relevant nature/wildlife hashtags at the end.
            - Always include #naturesrawclicks as the first hashtag.
            - Output ONLY the caption text followed by the hashtags. Nothing else.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final String openAiApiKey;
    private volatile ChatClient chatClient;

    public CaptionService(@Lazy ChatClient.Builder chatClientBuilder,
                          @Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.chatClientBuilder = chatClientBuilder;
        this.openAiApiKey = openAiApiKey == null ? "" : openAiApiKey;
        log.info("CaptionService: aiEnabled={} (resolution deferred until first use)", aiEnabled());
    }

    public String generateCaption(Path image) {
        if (!aiEnabled()) {
            log.debug("generateCaption: AI disabled, using fallback for {}", image.getFileName());
            return fallbackCaption(image);
        }
        try {
            ChatClient cc = chatClient();
            MimeType mime = mimeOf(image);
            log.debug("generateCaption: invoking ChatClient for {} (mime={})", image.getFileName(), mime);
            String reply = cc.prompt()
                    .user(u -> u.text(CAPTION_PROMPT)
                            .media(mime, new FileSystemResource(image)))
                    .call()
                    .content();
            if (reply == null || reply.isBlank()) {
                log.warn("generateCaption: empty AI response, using fallback for {}", image.getFileName());
                return fallbackCaption(image);
            }
            return reply.trim();
        } catch (Exception e) {
            log.warn("generateCaption: AI call failed, using fallback for {}: {}",
                    image.getFileName(), e.getMessage());
            return fallbackCaption(image);
        }
    }

    private boolean aiEnabled() {
        return !openAiApiKey.isBlank();
    }

    // Build the ChatClient on first use. Synchronised to avoid racing two
    // concurrent watcher scans into double-construction.
    private ChatClient chatClient() {
        ChatClient cc = chatClient;
        if (cc != null) {
            return cc;
        }
        synchronized (this) {
            if (chatClient == null) {
                chatClient = chatClientBuilder.build();
                log.debug("chatClient(): ChatClient built");
            }
            return chatClient;
        }
    }

    private String fallbackCaption(Path image) {
        String name = image.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        name = name.replace('_', ' ').replace('-', ' ').trim();
        String description = name.isEmpty() ? "A beautiful moment captured" : name;
        return description + "\n\n" + DEFAULT_HASHTAGS;
    }

    private MimeType mimeOf(Path image) {
        String name = image.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (name.endsWith(".gif")) {
            return MimeTypeUtils.IMAGE_GIF;
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }
}
