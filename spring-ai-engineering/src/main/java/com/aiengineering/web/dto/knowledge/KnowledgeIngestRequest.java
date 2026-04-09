package com.aiengineering.web.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record KnowledgeIngestRequest(
        @NotBlank @Size(max = 50000) String text, Map<String, Object> metadata) {

    public KnowledgeIngestRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
