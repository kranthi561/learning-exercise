package com.aiengineering.web.controller;

import com.aiengineering.repository.ChatMessageRepository;
import com.aiengineering.security.SecurityUtils;
import com.aiengineering.security.UserPrincipal;
import com.aiengineering.service.AgentService;
import com.aiengineering.service.ChatSessionService;
import com.aiengineering.web.dto.chat.AgentReplyResponse;
import com.aiengineering.web.dto.chat.AgentTaskRequest;
import com.aiengineering.web.dto.chat.ChatMessageRequest;
import com.aiengineering.web.dto.chat.ChatMessageResponse;
import com.aiengineering.web.dto.chat.ChatSessionCreateRequest;
import com.aiengineering.web.dto.chat.ChatSessionResponse;
import com.aiengineering.web.dto.chat.ImageGenerateRequest;
import com.aiengineering.web.dto.chat.ImageGenerateResponse;
import com.aiengineering.web.mapper.ChatMessageMapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final AgentService agentService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMapper chatMessageMapper;

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    ChatSessionResponse createSession(@Valid @RequestBody ChatSessionCreateRequest request) {
        log.debug("createSession: title={}", request.title());
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return chatSessionService.create(user.id(), request);
    }

    @GetMapping("/sessions")
    List<ChatSessionResponse> listSessions() {
        log.debug("listSessions");
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return chatSessionService.list(user.id());
    }

    @PostMapping("/sessions/{sessionId}/messages")
    AgentReplyResponse sendMessage(
            @PathVariable long sessionId, @Valid @RequestBody ChatMessageRequest request) {
        log.debug("sendMessage: sessionId={}", sessionId);
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return agentService.chat(user.id(), sessionId, request);
    }

    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> streamMessage(
            @PathVariable long sessionId, @Valid @RequestBody ChatMessageRequest request) {
        log.debug("streamMessage: sessionId={}", sessionId);
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return agentService.streamChat(user.id(), sessionId, request);
    }

    @PostMapping("/sessions/{sessionId}/images")
    ImageGenerateResponse generateImage(
            @PathVariable long sessionId, @Valid @RequestBody ImageGenerateRequest request) {
        log.debug("generateImage: sessionId={}", sessionId);
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return agentService.generateImage(user.id(), sessionId, request);
    }

    @PostMapping("/sessions/{sessionId}/tasks")
    AgentReplyResponse runTask(
            @PathVariable long sessionId, @Valid @RequestBody AgentTaskRequest request) {
        log.debug("runTask: sessionId={}", sessionId);
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return agentService.runTask(user.id(), sessionId, request);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    List<ChatMessageResponse> listMessages(@PathVariable long sessionId) {
        log.debug("listMessages: sessionId={}", sessionId);
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return chatMessageRepository.findHistoryForSession(sessionId, user.id()).stream()
                .map(chatMessageMapper::toResponse)
                .toList();
    }
}
