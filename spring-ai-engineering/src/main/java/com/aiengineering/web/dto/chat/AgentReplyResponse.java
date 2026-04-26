package com.aiengineering.web.dto.chat;

import java.util.List;

public record AgentReplyResponse(
        String assistantMessage,
        int ragChunksUsed,
        long latencyMs,
        List<String> toolsUsed) {}