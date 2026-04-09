package com.aiengineering.agent;

import com.aiengineering.repository.UserRepository;
import java.util.Objects;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AgentTools {

    private final UserRepository userRepository;

    public AgentTools(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Tool(description = "Look up a registered user by exact email for account verification or profile facts.")
    public String findUserProfileByEmail(
            @ToolParam(description = "User email address, case-insensitive match") String email) {
        return userRepository
                .findByEmailIgnoreCase(email.strip())
                .map(u -> "id=%d, email=%s, displayName=%s"
                        .formatted(u.getId(), u.getEmail(), u.getDisplayName()))
                .orElse("No user registered with that email.");
    }

    @Tool(description = "Simulated external API: returns a stable reference id for a topic (replace with RestTemplate/WebClient).")
    public String fetchExternalReference(
            @ToolParam(description = "Topic or entity key to resolve") String topic) {
        return "ExternalRef[%s]=demo-%s"
                .formatted(topic, Integer.toHexString(Objects.hash(Objects.requireNonNullElse(topic, ""))));
    }
}
