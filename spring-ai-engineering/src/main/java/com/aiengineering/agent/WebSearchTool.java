package com.aiengineering.agent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Tavily web-search tool. Calls the Tavily Search API and returns the top results
 * as formatted text so the model can cite sources in its answer.
 *
 * Set TAVILY_API_KEY in the environment (or app.web-search.tavily.api-key in config).
 * If the key is absent the tool returns a graceful "unavailable" message so the model
 * can fall back to its own knowledge instead of crashing the request.
 */
@Component
@Slf4j
public class WebSearchTool {

    private static final String TAVILY_URL = "https://api.tavily.com/search";
    private static final int MAX_RESULTS = 5;

    private final RestClient restClient;
    private final String apiKey;

    public WebSearchTool(RestClient.Builder restClientBuilder,
                         @Value("${app.web-search.tavily.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
    }

    @Tool(description = "Search the web for current, up-to-date information on any topic — news, recent events, product details, or facts not in the knowledge base.")
    public String searchWeb(
            @ToolParam(description = "Concise search query, e.g. 'Spring AI 1.0 release notes'") String query) {

        if (apiKey.isBlank()) {
            log.warn("searchWeb: TAVILY_API_KEY not configured — web search unavailable");
            return "Web search is unavailable (TAVILY_API_KEY not set). Answer from existing knowledge.";
        }

        log.debug("searchWeb: query={}", query);
        AgentStepRecorder.recordTool("searchWeb");

        Map<String, Object> body = Map.of(
                "api_key", apiKey,
                "query", query,
                "search_depth", "basic",
                "max_results", MAX_RESULTS
        );

        try {
            TavilyResponse response = restClient.post()
                    .uri(TAVILY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TavilyResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return "No web results found for: " + query;
            }

            log.debug("searchWeb: {} results returned", response.results().size());
            return response.results().stream()
                    .map(r -> "**%s**\n%s\nSource: %s".formatted(r.title(), r.content(), r.url()))
                    .collect(Collectors.joining("\n\n---\n\n"));

        } catch (RestClientException ex) {
            log.error("searchWeb: Tavily request failed: {}", ex.getMessage());
            return "Web search failed: " + ex.getMessage();
        }
    }

    private record TavilyResponse(List<TavilyResult> results) {}
    private record TavilyResult(String title, String url, String content) {}
}