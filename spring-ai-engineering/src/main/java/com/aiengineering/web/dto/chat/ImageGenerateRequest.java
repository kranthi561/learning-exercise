package com.aiengineering.web.dto.chat;

import jakarta.validation.constraints.NotBlank;

public record ImageGenerateRequest(@NotBlank String prompt) {}
