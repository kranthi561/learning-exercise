package com.aiengineering.service;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class KnowledgeService {

    // VectorStore is auto-configured by Spring AI with PgVector as the backend.
    // It handles embedding generation (via the configured EmbeddingModel) and
    // storage in the pgvector extension table in PostgreSQL.
    private final VectorStore vectorStore;

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Transactional
    public void ingest(String text, Map<String, Object> metadata) {
        log.debug("ingest: textLength={}, metadataKeys={}", text.length(), metadata.keySet());
        Document doc = new Document(text, metadata);
        vectorStore.add(List.of(doc));
    }
}
