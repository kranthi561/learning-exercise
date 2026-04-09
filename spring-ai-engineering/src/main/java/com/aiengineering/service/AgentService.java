package com.aiengineering.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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
import com.aiengineering.web.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private static final int MAX_HISTORY = 40;
    private static final int RAG_TOP_K = 4;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final AgentMetrics agentMetrics;

    @Transactional
    public AgentReplyResponse chat(long userId, long sessionId, ChatMessageRequest request) {
        ChatSession session = chatSessionRepository
                .findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSession(session);
        userMsg.setRole(MessageRole.USER);
        userMsg.setContent(request.content());
        chatMessageRepository.save(userMsg);

        List<ChatMessage> history = chatMessageRepository.findHistoryForSession(sessionId, userId);
        if (history.size() > MAX_HISTORY) {
            history = history.subList(history.size() - MAX_HISTORY, history.size());
        }

        List<Document> ragDocs = Optional.ofNullable(vectorStore.similaritySearch(
                SearchRequest.builder().query(request.content()).topK(RAG_TOP_K).build()))
                .orElse(List.of());
        String ragBlock = ragDocs.isEmpty()
                ? "(no retrieved documents)"
                : ragDocs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

        String systemWithRag =
                """
                Retrieved knowledge (RAG) — ground answers when relevant; say if empty:
                %s
                """
                        .formatted(ragBlock);

        List<Message> messages = new ArrayList<>();
        for (ChatMessage m : history) {
            switch (m.getRole()) {
                case USER -> messages.add(new UserMessage(m.getContent()));
                case ASSISTANT -> messages.add(new AssistantMessage(m.getContent()));
                case SYSTEM -> {
                    /* persisted system lines are optional; RAG is applied via .system() above */
                }
            }
        }

        long start = System.nanoTime();
        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .system(systemWithRag)
                    .messages(messages)
                    .call()
                    .chatResponse();
            String assistantText = Optional.ofNullable(chatResponse)
                    .map(ChatResponse::getResult)
                    .map(r -> r.getOutput())
                    .map(o -> o.getText())
                    .orElse("");
            log.info("Chat call: {}", assistantText);

            ChatMessage assistant = new ChatMessage();
            assistant.setSession(session);
            assistant.setRole(MessageRole.ASSISTANT);
            assistant.setContent(assistantText);
            chatMessageRepository.save(assistant);

            long elapsed = System.nanoTime() - start;
            Integer totalTokens = extractTotalTokens(chatResponse);
            agentMetrics.recordSuccess(elapsed, totalTokens);

            return new AgentReplyResponse(assistantText, ragDocs.size(), elapsed / 1_000_000);
        } catch (RuntimeException ex) {
            log.error("Chat call failed: {}", ex.getMessage());
            long elapsed = System.nanoTime() - start;
            agentMetrics.recordFailure(elapsed);
            throw ex;
        }
    }

    private static Integer extractTotalTokens(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        var usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        return usage.getTotalTokens();
    }
}
