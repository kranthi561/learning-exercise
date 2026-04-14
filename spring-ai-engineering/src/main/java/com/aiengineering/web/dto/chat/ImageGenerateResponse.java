package com.aiengineering.web.dto.chat;

import java.util.List;

public record ImageGenerateResponse(List<String> imageUrls, long latencyMs) {}
