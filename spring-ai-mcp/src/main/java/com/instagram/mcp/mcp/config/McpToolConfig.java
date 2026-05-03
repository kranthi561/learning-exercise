package com.instagram.mcp.mcp.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.instagram.mcp.mcp.tool.InstagramMcpTools;

import lombok.extern.slf4j.Slf4j;

// Registers all MCP tool objects with the Spring AI MCP server auto-discovery mechanism.
@Configuration
@Slf4j
public class McpToolConfig {

    @Bean
    ToolCallbackProvider mcpToolCallbackProvider(InstagramMcpTools instagramMcpTools) {
        log.debug("mcpToolCallbackProvider: building MCP callback provider");
        return MethodToolCallbackProvider.builder()
                .toolObjects(instagramMcpTools)
                .build();
    }
}
