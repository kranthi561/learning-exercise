package com.aiengineering.agent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import com.aiengineering.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

// Registers this class as a Spring-managed bean so it can be injected
// into AiClientConfig and passed to the ChatClient as a tool provider.
@Component

// Lombok: injects a SLF4J logger as 'log'.
@Slf4j
public class AgentTools {

    private static final int SEARCH_TOP_K = 4;

    private final UserRepository userRepository;
    private final VectorStore vectorStore;

    // Constructor injection — preferred over @Autowired on fields; makes
    // dependencies explicit and the class easier to unit test.
    public AgentTools(UserRepository userRepository, VectorStore vectorStore) {
        this.userRepository = userRepository;
        this.vectorStore = vectorStore;
    }

    // Marks this method as a callable tool for the LLM.
    // The 'description' is sent to the model so it knows when and how to invoke the function.
    @Tool(description = "Look up a registered user by exact email for account verification or profile facts.")
    public String findUserProfileByEmail(
            // @ToolParam describes the individual parameter to the model so it passes the right value.
            @ToolParam(description = "User email address, case-insensitive match") String email) {
        log.debug("findUserProfileByEmail: email={}", email);
        AgentStepRecorder.recordTool("findUserProfileByEmail");
        return userRepository
                .findByEmailIgnoreCase(email.strip()) // strip() removes accidental whitespace from the model's output
                .map(u -> "id=%d, email=%s, displayName=%s"
                        .formatted(u.getId(), u.getEmail(), u.getDisplayName()))
                .orElse("No user registered with that email.");
    }

    // Second tool: simulates calling an external API.
    // The LLM can invoke this when it needs an external reference ID for a topic.
    @Tool(description = "Simulated external API: returns a stable reference id for a topic (replace with RestTemplate/WebClient).")
    public String fetchExternalReference(
            @ToolParam(description = "Topic or entity key to resolve") String topic) {
        log.debug("fetchExternalReference: topic={}", topic);
        // Objects.hash produces a stable int for the same topic string;
        // toHexString gives a short, URL-safe representation.
        return "ExternalRef[%s]=demo-%s"
                .formatted(topic, Integer.toHexString(Objects.hash(Objects.requireNonNullElse(topic, ""))));
    }

    @Tool(description = "Get the current date and time in ISO-8601 format.")
    public String getCurrentDateTime() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        log.debug("getCurrentDateTime: {}", now);
        AgentStepRecorder.recordTool("getCurrentDateTime");
        return now;
    }

    // Whitelist guards against SpEL code injection — only digits, operators, and parentheses pass.
    @Tool(description = "Evaluate a basic arithmetic expression (+, -, *, /, parentheses) and return the result.")
    public String calculate(
            @ToolParam(description = "Arithmetic expression, e.g. '(2 + 3) * 4 / 2'") String expression) {
        log.debug("calculate: expression={}", expression);
        AgentStepRecorder.recordTool("calculate");
        if (!expression.matches("[0-9+\\-*/().\\s]+")) {
            return "Only numeric arithmetic is supported.";
        }
        try {
            Object result = new SpelExpressionParser().parseExpression(expression).getValue();
            return String.valueOf(result);
        } catch (SpelParseException | SpelEvaluationException | ArithmeticException e) {
            log.warn("calculate: failed for '{}': {}", expression, e.getMessage());
            return "Cannot evaluate: " + expression;
        }
    }

    @Tool(description = "Search the knowledge base for documents relevant to a query. Use this before answering factual questions.")
    public String searchKnowledgeBase(
            @ToolParam(description = "Natural-language search query") String query) {
        log.debug("searchKnowledgeBase: query length={}", query.length());
        AgentStepRecorder.recordTool("searchKnowledgeBase");
        List<Document> docs = Optional.ofNullable(
                vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(SEARCH_TOP_K).build()))
                .orElse(List.of());
        if (docs.isEmpty()) {
            return "No documents found in the knowledge base for: " + query;
        }
        log.debug("searchKnowledgeBase: found {} docs", docs.size());
        return docs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
    }

    @Tool(description = "Save an intermediate result or fact to working memory under a key. Use this to remember something across tool calls within one turn.")
    public String saveToMemory(
            @ToolParam(description = "Key to store the value under") String key,
            @ToolParam(description = "Value to store") String value) {
        log.debug("saveToMemory: key={}", key);
        AgentStepRecorder.recordTool("saveToMemory");
        AgentStepRecorder.saveMemory(key, value);
        return "Saved to memory: " + key;
    }

    @Tool(description = "Read a previously saved value from working memory by key.")
    public String readFromMemory(
            @ToolParam(description = "Key to retrieve") String key) {
        log.debug("readFromMemory: key={}", key);
        AgentStepRecorder.recordTool("readFromMemory");
        String val = AgentStepRecorder.readMemory(key);
        return val != null ? val : "No value found in memory for key: " + key;
    }
}