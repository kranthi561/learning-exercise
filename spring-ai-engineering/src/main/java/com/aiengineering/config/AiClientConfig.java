package com.aiengineering.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aiengineering.agent.AgentTools;

import lombok.extern.slf4j.Slf4j;

// Marks this class as a source of Spring bean definitions.
// Spring reads @Bean methods here during context startup.
@Configuration

// Lombok: injects a SLF4J logger as 'log'.
@Slf4j
public class AiClientConfig {

    // @Bean tells Spring to manage the returned ChatClient instance
    // and make it available for injection across the application.
    // Parameters (ChatModel, AgentTools) are auto-injected from the context.
    @Bean
    ChatClient chatClient(ChatModel chatModel, AgentTools agentTools) {
        log.debug("chatClient: building ChatClient with model={}, tools={}", chatModel.getClass().getSimpleName(), agentTools.getClass().getSimpleName());
        return ChatClient.builder(chatModel)
                // defaultSystem sets the system prompt that is prepended to every
                // conversation — it establishes the assistant's persona and instructions.
                .defaultSystem(
                        """
                        You are the AI Engineering assistant.
                        Use tools when you need live user data from our database or simulated external APIs.
                        When RAG context is provided, base answers on it and say when information is missing.
                        Keep replies concise unless the user asks for depth.
                        """)
                // defaultTools registers AgentTools methods annotated with @Tool so
                // the LLM can call them (function calling / tool use) on every request.
                .defaultTools(agentTools)
                .build();
    }
}
