package com.aiengineering.advisor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import lombok.extern.slf4j.Slf4j;

/**
 * Advisor that retrieves semantically similar documents from a VectorStore and injects
 * them into the system prompt before the request reaches the model (RAG pattern).
 * Replaces the removed QuestionAnswerAdvisor from Spring AI milestone builds.
 */
@Slf4j
public class VectorStoreRagAdvisor implements BaseAdvisor {

    private final VectorStore vectorStore;
    private final int topK;

    public VectorStoreRagAdvisor(VectorStore vectorStore, int topK) {
        this.vectorStore = vectorStore;
        this.topK = topK;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // Restore the requestId into MDC on this Reactor thread so that every log
        // line emitted here carries [requestId], matching the servlet-thread logs.
        // The value was captured on the servlet thread in AgentService and passed
        // through ChatClientRequest.context() to survive the thread boundary.
        String requestId = (String) request.context().get("requestId");
        if (requestId != null) {
            MDC.put("requestId", requestId);
        }
        try {
            String query = request.prompt().getUserMessage().getText();

            List<Document> docs = Optional.ofNullable(
                    vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build()))
                    .orElse(List.of());
            log.debug("VectorStoreRagAdvisor: retrieved {} docs for query length={}", docs.size(), query.length());

            String ragBlock = docs.isEmpty()
                    ? "(no retrieved documents)"
                    : docs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

            String currentSystemText = request.prompt().getSystemMessage().getText();
            String augmented = currentSystemText + """

                    Retrieved knowledge (RAG) — ground answers on this when relevant:
                    %s
                    """.formatted(ragBlock);

            return request.mutate()
                    .prompt(request.prompt().augmentSystemMessage(augmented))
                    .build();
        } finally {
            // Always clear MDC on this pooled Reactor thread to prevent leaking
            // the requestId into a future unrelated request.
            if (requestId != null) {
                MDC.remove("requestId");
            }
        }
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
