package com.aiengineering.web.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiengineering.agent.AgentTools;
import com.aiengineering.agent.WebSearchTool;
import com.aiengineering.web.dto.chat.ToolInfoResponse;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/agent")
@Slf4j
public class AgentController {

    // UI input templates keyed by tool name — pre-fill the chat input on selection.
    // Tool name comes from the @Tool annotation; template is a UI hint, not part of Spring AI metadata.
    private static final Map<String, String> TEMPLATES = Map.of(
            "searchWeb",              "Search the web for: ",
            "searchKnowledgeBase",    "Search the knowledge base for: ",
            "calculate",              "Calculate: ",
            "getCurrentDateTime",     "What is the current date and time?",
            "findUserProfileByEmail", "Find user profile for email: ",
            "fetchExternalReference", "Fetch external reference for: ",
            "saveToMemory",           "Save to memory key=",
            "readFromMemory",         "Read from memory key: "
    );

    // Built once at startup from @Tool-annotated methods via Spring AI reflection.
    private final List<ToolInfoResponse> toolInfos;

    public AgentController(AgentTools agentTools, WebSearchTool webSearchTool) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(agentTools, webSearchTool)
                .build()
                .getToolCallbacks();

        this.toolInfos = Arrays.stream(callbacks)
                .map(cb -> new ToolInfoResponse(
                        cb.getToolDefinition().name(),
                        cb.getToolDefinition().description(),
                        TEMPLATES.getOrDefault(cb.getToolDefinition().name(),
                                cb.getToolDefinition().name() + ": ")))
                .toList();

        log.debug("AgentController: registered {} tools", toolInfos.size());
    }

    @GetMapping("/tools")
    List<ToolInfoResponse> listTools() {
        return toolInfos;
    }
}