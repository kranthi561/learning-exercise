package com.aiengineering.web.dto.chat;

import com.aiengineering.domain.MessageRole;
import java.time.Instant;

public record ChatMessageResponse(Long id, MessageRole role, String content, Instant createdAt) {}
