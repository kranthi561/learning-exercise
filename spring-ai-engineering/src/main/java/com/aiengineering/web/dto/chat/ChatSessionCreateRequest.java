package com.aiengineering.web.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSessionCreateRequest(@NotBlank @Size(max = 200) String title) {}
