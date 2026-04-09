package com.aiengineering.web.dto.chat;

public record AgentReplyResponse(String assistantMessage, int ragChunksUsed, long latencyMs) {}
