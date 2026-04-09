package com.aiengineering.config;

import com.aiengineering.agent.AgentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfig {

    @Bean
    ChatClient chatClient(ChatModel chatModel, AgentTools agentTools) {
        return ChatClient.builder(chatModel)
                .defaultSystem(
                        """
                        You are the AI Engineering assistant.
                        Use tools when you need live user data from our database or simulated external APIs.
                        When RAG context is provided, base answers on it and say when information is missing.
                        Keep replies concise unless the user asks for depth.
                        """)
                .defaultTools(agentTools)
                .build();
    }
}
