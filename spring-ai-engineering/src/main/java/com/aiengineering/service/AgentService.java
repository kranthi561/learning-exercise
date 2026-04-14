package com.aiengineering.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;           // Spring AI fluent client for building and sending prompts
import org.springframework.ai.chat.messages.AssistantMessage;   // Wraps a past AI reply for inclusion in history
import org.springframework.ai.chat.messages.Message;            // Common interface for all message types (user/assistant/system)
import org.springframework.ai.chat.messages.UserMessage;        // Wraps a past user turn for inclusion in history
import org.springframework.ai.chat.model.ChatResponse;          // Full response object from the model (content + metadata)
import org.springframework.ai.document.Document;                // A knowledge document stored in the vector store
import org.springframework.ai.image.ImageModel;                 // Spring AI abstraction for image generation (DALL-E etc.)
import org.springframework.ai.image.ImagePrompt;                // Wraps the text prompt sent to the image model
import org.springframework.ai.image.ImageResponse;              // Full response from the image model (URLs or base64)
import org.springframework.ai.openai.OpenAiImageOptions;        // OpenAI-specific options (model, size, quality, n)
import org.springframework.ai.vectorstore.SearchRequest;        // Builder for similarity search parameters
import org.springframework.ai.vectorstore.VectorStore;          // Abstraction over PgVector for RAG retrieval
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiengineering.domain.ChatMessage;
import com.aiengineering.domain.ChatSession;
import com.aiengineering.domain.MessageRole;
import com.aiengineering.observability.AgentMetrics;
import com.aiengineering.repository.ChatMessageRepository;
import com.aiengineering.repository.ChatSessionRepository;
import com.aiengineering.web.dto.chat.AgentReplyResponse;
import com.aiengineering.web.dto.chat.ChatMessageRequest;
import com.aiengineering.web.dto.chat.ImageGenerateRequest;
import com.aiengineering.web.dto.chat.ImageGenerateResponse;
import com.aiengineering.web.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// @Service is a specialisation of @Component — marks this as a business-logic bean
// so Spring auto-discovers it and makes it injectable into ChatController.
@Service

// Lombok: generates a constructor for every final field — Spring uses it to inject
// all dependencies without requiring explicit @Autowired on each field.
@RequiredArgsConstructor

// Lombok: injects a private static final SLF4J logger named 'log'
// so we can call log.debug() / log.info() / log.error() throughout the class.
@Slf4j
public class AgentService {

    // Hard cap on how many messages we send back to the model per turn.
    // Keeps token cost predictable; older context is silently dropped when exceeded.
    private static final int MAX_HISTORY = 40;

    // Number of knowledge documents retrieved from the vector store per query.
    // Higher = more context but more tokens and slower model response.
    private static final int RAG_TOP_K = 4;

    // Spring AI's fluent chat client — pre-configured in AiClientConfig with
    // the default system prompt and the AgentTools function-calling tools.
    private final ChatClient chatClient;

    // Spring AI image model — auto-configured by spring-ai-starter-model-openai.
    // Backed by OpenAI DALL-E; called only when the user explicitly requests image generation.
    private final ImageModel imageModel;

    // PgVector-backed store; used to retrieve semantically similar documents (RAG).
    // Spring AI auto-configures this from the pgvector properties in application-dev.yml.
    private final VectorStore vectorStore;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;

    // Micrometer metrics bean — records call counts, latency, and token usage
    // for visibility in Prometheus / Grafana dashboards.
    private final AgentMetrics agentMetrics;

    // @Transactional wraps the entire method in a single DB transaction.
    // The user-message save, history fetch, and assistant-message save all succeed
    // or all roll back together — no orphaned messages if an error occurs mid-way.
    @Transactional
    public AgentReplyResponse chat(long userId, long sessionId, ChatMessageRequest request) {
        log.debug("chat: userId={}, sessionId={}, contentLength={}", userId, sessionId, request.content().length());

        // Ownership check: verifies the session belongs to this user before proceeding.
        // Throws ResourceNotFoundException → 404 if the session doesn't exist or is owned by another user.
        ChatSession session = chatSessionRepository
                .findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        // Persist the user message BEFORE calling the model so it is recorded
        // even if the AI call subsequently fails (avoids a lost-message scenario).
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSession(session);            // FK association — links this message to the session row
        userMsg.setRole(MessageRole.USER);      // Marks who authored this message
        userMsg.setContent(request.content());  // The raw text the user typed
        chatMessageRepository.save(userMsg);

        // Load all past messages for this session to build the conversation history.
        // The query also enforces userId so a user cannot read another user's history.
        List<ChatMessage> history = chatMessageRepository.findHistoryForSession(sessionId, userId);

        // Trim to the most recent MAX_HISTORY entries to stay inside the model's
        // context window and control token cost (oldest messages are silently dropped).
        if (history.size() > MAX_HISTORY) {
            history = history.subList(history.size() - MAX_HISTORY, history.size());
        }
        log.debug("chat: historySize={}", history.size());

        // --- RAG (Retrieval-Augmented Generation) ---
        // Search the vector store for documents semantically similar to the user's message.
        // The VectorStore converts the query to an embedding vector and performs
        // a cosine similarity search against stored document vectors.
        // orElse(List.of()) guards against null returns from some VectorStore implementations.
        List<Document> ragDocs = Optional.ofNullable(vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.content()) // convert this text to an embedding and search
                        .topK(RAG_TOP_K)          // return at most RAG_TOP_K closest documents
                        .build()))
                .orElse(List.of());
        log.debug("chat: ragDocsFound={}", ragDocs.size());

        // Join retrieved document texts with a visible separator so the model
        // can tell where one knowledge chunk ends and the next begins.
        String ragBlock = ragDocs.isEmpty()
                ? "(no retrieved documents)"
                : ragDocs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

        // Inject RAG results as a per-call system prompt override.
        // This is prepended to the conversation so the model treats retrieved
        // facts as ground truth for this specific turn.
        String systemWithRag =
                """
                Retrieved knowledge (RAG) — ground answers when relevant; say if empty:
                %s
                """
                .formatted(ragBlock);

        // Convert persisted ChatMessage entities to Spring AI Message objects.
        // The model needs the history as typed message objects, not domain entities.
        List<Message> messages = new ArrayList<>();
        for (ChatMessage m : history) {
            switch (m.getRole()) {
                // UserMessage = a turn the human sent in a previous round
                case USER -> messages.add(new UserMessage(m.getContent()));
                // AssistantMessage = a reply the AI produced in a previous round
                case ASSISTANT -> messages.add(new AssistantMessage(m.getContent()));
                // SYSTEM messages stored in DB are informational; the live RAG prompt
                // is injected via .system() below and takes precedence.
                case SYSTEM -> { /* intentionally skipped */ }
            }
        }

        // Capture nanosecond start time for high-resolution latency measurement
        // that will be recorded in the Micrometer Timer.
        long start = System.nanoTime();
        try {
            // Build and execute the prompt against the configured OpenAI model.
            // .system()   — per-call system prompt (RAG context overwrites the default)
            // .messages() — full conversation history for multi-turn context
            // .call()     — sends the request synchronously (blocks until the model replies)
            // .chatResponse() — returns the full ChatResponse with content + usage metadata
            ChatResponse chatResponse = chatClient.prompt()
                    .system(systemWithRag)
                    .messages(messages)
                    .call()
                    .chatResponse();

            // Safely drill through the response chain to extract the text.
            // Optional.ofNullable guards each nullable step so no NullPointerException
            // is thrown if the model returns an empty or malformed response.
            String assistantText = Optional.ofNullable(chatResponse)
                    .map(ChatResponse::getResult)       // get the first completion result
                    .map(r -> r.getOutput())            // get the output message from that result
                    .map(o -> o.getText())              // get the raw text string
                    .orElse("");                        // fall back to empty string if any step is null
            log.debug("Chat call: {}", assistantText); // NOT required allways, it is printing llm output

            // Persist the assistant's reply so the next turn can include it in history
            // and so the user can retrieve it via GET /sessions/{id}/messages.
            ChatMessage assistant = new ChatMessage();
            assistant.setSession(session);
            assistant.setRole(MessageRole.ASSISTANT); // marks this as an AI-generated message
            assistant.setContent(assistantText);
            chatMessageRepository.save(assistant);

            long elapsed = System.nanoTime() - start; // total wall-clock nanoseconds for the AI call
            Integer totalTokens = extractTotalTokens(chatResponse);

            // Record success metrics: increments success counter, records latency timer,
            // and adds token count to the distribution summary for cost tracking.
            agentMetrics.recordSuccess(elapsed, totalTokens);
            log.debug("chat: completed in {}ms, tokens={}", elapsed / 1_000_000, totalTokens);

            // Convert elapsed nanos to millis (divide by 1_000_000) before returning
            // so the DTO exposes a human-readable unit.
            return new AgentReplyResponse(assistantText, ragDocs.size(), elapsed / 1_000_000);

        } catch (RuntimeException ex) {
            log.error("Chat call failed: {}", ex.getMessage());
            long elapsed = System.nanoTime() - start;
            // Record failure metrics before re-throwing so Prometheus captures
            // the failure count and latency even for errored calls.
            agentMetrics.recordFailure(elapsed);
            throw ex; // re-throw so GlobalExceptionHandler can return the appropriate HTTP status
        }
    }

    // Calls DALL-E to generate images for the given prompt.
    // Ownership check is the same pattern as chat() — the session must belong to this user.
    public ImageGenerateResponse generateImage(long userId, long sessionId, ImageGenerateRequest request) {
        log.debug("generateImage: userId={}, sessionId={}, prompt={}", userId, sessionId, request.prompt());

        // Verify the session exists and belongs to this user before spending API credits.
        chatSessionRepository
                .findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        // Build the image request — uses dall-e-3 which supports only n=1;
        // switch to dall-e-2 if you need n > 1.
        ImagePrompt imagePrompt = new ImagePrompt(
                request.prompt(),
                OpenAiImageOptions.builder()
                        .model("dall-e-3")
                        .quality("standard")
                        .height(1024)
                        .width(1024)
                        .build());

        long start = System.nanoTime();
        try {
            ImageResponse imageResponse = imageModel.call(imagePrompt);

            // Collect URLs from all returned image results.
            List<String> urls = imageResponse.getResults().stream()
                    .map(result -> result.getOutput().getUrl())
                    .toList();

            long elapsed = System.nanoTime() - start;
            log.debug("generateImage: got {} url(s) in {}ms", urls.size(), elapsed / 1_000_000);
            return new ImageGenerateResponse(urls, elapsed / 1_000_000);

        } catch (RuntimeException ex) {
            log.error("Image generation failed: {}", ex.getMessage());
            throw ex;
        }
    }

    // Helper: safely extracts the total token count from the model's usage metadata.
    // Returns null (not zero) if usage data is unavailable so callers can distinguish
    // "not reported" from "reported as zero tokens".
    private static Integer extractTotalTokens(ChatResponse chatResponse) {
        log.debug("extractTotalTokens: chatResponse={}", chatResponse != null ? "present" : "null");
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null; // model did not return metadata (e.g. some streaming modes)
        }
        var usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return null; // usage object absent — treat as unknown
        }
        return usage.getTotalTokens(); // prompt tokens + completion tokens combined
    }
}
