package com.aiengineering.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aiengineering.agent.AgentTools;
import com.aiengineering.agent.WebSearchTool;

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
    ChatClient chatClient(ChatModel chatModel, AgentTools agentTools, WebSearchTool webSearchTool) {
        log.debug("chatClient: building ChatClient with model={}, tools={}, {}", chatModel.getClass().getSimpleName(),
        agentTools.getClass().getSimpleName(), webSearchTool.getClass().getSimpleName());
        return ChatClient.builder(chatModel)
                // defaultSystem sets the system prompt that is prepended to every
                // conversation — it establishes the assistant's persona and instructions.
                .defaultSystem(
                        """
                        You are an autonomous AI agent with access to tools.

                        When given a task or question, follow this reasoning loop:
                        1. PLAN  — decide which tools (if any) are needed
                        2. ACT   — call the required tools, in sequence if results depend on each other
                        3. OBSERVE — interpret tool results; save intermediate facts with saveToMemory if needed
                        4. ANSWER — respond with a clear, grounded answer citing sources where available

                        Available tools and when to use them:
                        - searchWeb            : current news, recent events, facts not in the knowledge base
                        - searchKnowledgeBase  : internal documents ingested by the team
                        - findUserProfileByEmail: user account lookups
                        - getCurrentDateTime   : any time-relative questions
                        - calculate            : arithmetic
                        - saveToMemory / readFromMemory : scratchpad for multi-step reasoning

                        Rules:
                        - Prefer searchWeb for questions about recent events or things that may have changed.
                        - Prefer searchKnowledgeBase for domain-specific internal content.
                        - Ground answers on retrieved data; clearly state what you don't know.
                        - For multi-step tasks, show your reasoning before the final answer.
                        - Keep replies concise unless the user asks for depth.
                        """)
                // defaultTools registers AgentTools and webSearchTool methods annotated with @Tool so
                // the LLM can call them (function calling / tool use) on every request.
                .defaultTools(agentTools, webSearchTool)
                .build();
    }
}