package com.aiengineering.service;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {

    private final VectorStore vectorStore;

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Transactional
    public void ingest(String text, Map<String, Object> metadata) {
        Document doc = new Document(text, metadata);
        vectorStore.add(List.of(doc));
    }
}
