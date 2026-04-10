package com.aiengineering.service;

import com.aiengineering.domain.ChatSession;
import com.aiengineering.domain.User;
import com.aiengineering.repository.ChatSessionRepository;
import com.aiengineering.repository.UserRepository;
import com.aiengineering.web.dto.chat.ChatSessionCreateRequest;
import com.aiengineering.web.dto.chat.ChatSessionResponse;
import com.aiengineering.web.exception.ResourceNotFoundException;
import com.aiengineering.web.mapper.ChatSessionMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final ChatSessionMapper chatSessionMapper;

    @Transactional
    public ChatSessionResponse create(long userId, ChatSessionCreateRequest request) {
        log.debug("create: userId={}, title={}", userId, request.title());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setTitle(request.title().strip());
        ChatSession saved = chatSessionRepository.save(session);
        return new ChatSessionResponse(saved.getId(), saved.getTitle(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> list(long userId) {
        log.debug("list: userId={}", userId);
        return chatSessionRepository.listForUser(userId).stream()
                .map(chatSessionMapper::fromListProjection)
                .toList();
    }
}
