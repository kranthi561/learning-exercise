package com.aiengineering.web.dto.chat;

import java.time.Instant;

public record ChatSessionResponse(Long id, String title, Instant createdAt) {}
