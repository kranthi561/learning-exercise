package com.aiengineering.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// Teaching-character tool: responds as a patient, structured tutor rather than
// a generic assistant. Keeps explanations layered — concept → analogy → example → prompt.
@Component
@Slf4j
public class LearningTool {

    @Tool(description = """
            Explain a concept as a patient tutor using a four-part teaching structure:
            (1) Core idea in plain language,
            (2) Real-world analogy,
            (3) Concrete code or practical example,
            (4) One follow-up question to deepen understanding.
            Use this tool whenever the user asks to learn, understand, or explain something.
            """)
    public String learnConcept(
            @ToolParam(description = "The concept, topic, or question the user wants to learn about") String topic,
            @ToolParam(description = "Learner level: beginner | intermediate | advanced. Default to 'beginner' if unsure.") String level) {

        log.debug("learnConcept: topic='{}', level='{}'", topic, level);
        AgentStepRecorder.recordTool("learnConcept");

        String adjustedLevel = (level == null || level.isBlank()) ? "beginner" : level.strip().toLowerCase();

        return """
                [LEARNING MODE — %s level]

                You are now acting as a patient, encouraging tutor. Structure your reply exactly like this:

                ## Concept
                Explain "%s" clearly and concisely for a %s learner. Avoid jargon unless you define it immediately.

                ## Analogy
                Give one real-world analogy that maps the core idea to something the learner already knows.

                ## Example
                Show a short, runnable code snippet or a step-by-step walkthrough that demonstrates the concept in practice.

                ## Think About It
                End with exactly one open-ended question that encourages the learner to apply or extend what they just learned.

                Keep the tone warm, precise, and encouraging throughout.
                """.formatted(adjustedLevel, topic, adjustedLevel);
    }
}