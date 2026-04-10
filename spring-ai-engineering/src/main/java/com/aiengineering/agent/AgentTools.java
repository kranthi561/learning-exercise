package com.aiengineering.agent;

import com.aiengineering.repository.UserRepository;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

// Registers this class as a Spring-managed bean so it can be injected
// into AiClientConfig and passed to the ChatClient as a tool provider.
@Component

// Lombok: injects a SLF4J logger as 'log'.
@Slf4j
public class AgentTools {

    private final UserRepository userRepository;

    // Constructor injection — preferred over @Autowired on fields; makes
    // dependencies explicit and the class easier to unit test.
    public AgentTools(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Marks this method as a callable tool for the LLM.
    // The 'description' is sent to the model so it knows when and how to invoke the function.
    @Tool(description = "Look up a registered user by exact email for account verification or profile facts.")
    public String findUserProfileByEmail(
            // @ToolParam describes the individual parameter to the model so it passes the right value.
            @ToolParam(description = "User email address, case-insensitive match") String email) {
        log.debug("findUserProfileByEmail: email={}", email);
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
}
