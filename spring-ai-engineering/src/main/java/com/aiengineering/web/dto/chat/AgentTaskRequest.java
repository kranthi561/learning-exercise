package com.aiengineering.web.dto.chat;

import jakarta.validation.constraints.NotBlank;

public record AgentTaskRequest(@NotBlank String task) {}