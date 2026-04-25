package com.aiengineering.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ThreadLocal store for a single agent turn.
 *
 * Tools call recordTool() as they execute; AgentService calls start() before the LLM
 * call and getAndClear() after it to harvest the list for the response DTO.
 *
 * saveMemory / readMemory give tools a scratchpad inside one blocking turn — they can
 * write intermediate results and pick them up in later tool invocations within the same
 * request thread.
 *
 * Not safe for streaming turns (Reactor thread-hops lose ThreadLocal state).
 */
public final class AgentStepRecorder {

    private static final ThreadLocal<List<String>> STEPS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Map<String, String>> MEMORY = ThreadLocal.withInitial(HashMap::new);

    private AgentStepRecorder() {}

    public static void start() {
        STEPS.get().clear();
        MEMORY.get().clear();
    }

    public static void recordTool(String toolName) {
        STEPS.get().add(toolName);
    }

    public static List<String> getAndClear() {
        List<String> result = new ArrayList<>(STEPS.get());
        STEPS.get().clear();
        MEMORY.get().clear();
        return result;
    }

    public static void saveMemory(String key, String value) {
        MEMORY.get().put(key, value);
    }

    public static String readMemory(String key) {
        return MEMORY.get().get(key);
    }
}