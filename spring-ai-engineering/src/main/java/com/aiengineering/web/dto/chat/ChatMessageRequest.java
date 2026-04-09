package com.aiengineering.web.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(@NotBlank @Size(max = 16000) String content) {}
