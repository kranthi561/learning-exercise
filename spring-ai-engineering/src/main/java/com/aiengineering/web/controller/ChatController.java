package com.aiengineering.web.controller;

import com.aiengineering.repository.ChatMessageRepository;
import com.aiengineering.security.SecurityUtils;
import com.aiengineering.security.UserPrincipal;
import com.aiengineering.service.AgentService;
import com.aiengineering.service.ChatSessionService;
import com.aiengineering.web.dto.chat.AgentReplyResponse;
import com.aiengineering.web.dto.chat.ChatMessageRequest;
import com.aiengineering.web.dto.chat.ChatMessageResponse;
import com.aiengineering.web.dto.chat.ChatSessionCreateRequest;
import com.aiengineering.web.dto.chat.ChatSessionResponse;
import com.aiengineering.web.mapper.ChatMessageMapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final AgentService agentService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMapper chatMessageMapper;

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    ChatSessionResponse createSession(@Valid @RequestBody ChatSessionCreateRequest request) {
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return chatSessionService.create(user.id(), request);
    }

    @GetMapping("/sessions")
    List<ChatSessionResponse> listSessions() {
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return chatSessionService.list(user.id());
    }

    @PostMapping("/sessions/{sessionId}/messages")
    AgentReplyResponse sendMessage(
            @PathVariable long sessionId, @Valid @RequestBody ChatMessageRequest request) {
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return agentService.chat(user.id(), sessionId, request);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    List<ChatMessageResponse> listMessages(@PathVariable long sessionId) {
        UserPrincipal user = SecurityUtils.requireCurrentUser();
        return chatMessageRepository.findHistoryForSession(sessionId, user.id()).stream()
                .map(chatMessageMapper::toResponse)
                .toList();
    }
}
