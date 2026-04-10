package com.aiengineering.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AgentMetrics {

    // Counter incremented on every successful AI call —
    // used to track success rate in Prometheus / Grafana.
    private final Counter callsSuccess;

    // Counter incremented on every failed AI call —
    // alerts can be set on this metric.
    private final Counter callsFailure;

    // Timer measures end-to-end latency of each AI call in nanoseconds,
    // then Micrometer converts it for Prometheus (seconds by default).
    private final Timer latency;

    // DistributionSummary tracks token consumption across calls —
    // useful for cost monitoring since OpenAI charges per token.
    private final DistributionSummary tokenUsage;

    // MeterRegistry is auto-configured by Spring Boot (Micrometer) and injected here.
    // All metrics are registered at construction time so they appear in /actuator/prometheus
    // even before the first call is made.
    public AgentMetrics(MeterRegistry registry) {
        log.debug("AgentMetrics: registering metrics with registry={}", registry.getClass().getSimpleName());
        // "outcome" tag differentiates success vs failure on the same metric name,
        // allowing a single PromQL query with label filtering.
        this.callsSuccess = registry.counter("agent.calls", "outcome", "success");
        this.callsFailure = registry.counter("agent.calls", "outcome", "failure");
        this.latency = registry.timer("agent.latency");
        this.tokenUsage = DistributionSummary.builder("agent.tokens.total")
                .description("Total tokens reported by the model when available")
                .register(registry);
    }

    // Called by AgentService after a successful AI response.
    // durationNanos: wall-clock time of the call; totalTokens: from the model's usage metadata.
    public void recordSuccess(long durationNanos, Integer totalTokens) {
        log.debug("recordSuccess: durationNanos={}, totalTokens={}", durationNanos, totalTokens);
        callsSuccess.increment();
        latency.record(durationNanos, TimeUnit.NANOSECONDS);
        // Only record token usage when the model actually reports it
        // (some models or streaming modes may not return usage).
        if (totalTokens != null && totalTokens > 0) {
            tokenUsage.record(totalTokens);
        }
    }

    // Called by AgentService when the AI call throws an exception.
    public void recordFailure(long durationNanos) {
        log.debug("recordFailure: durationNanos={}", durationNanos);
        callsFailure.increment();
        // Still record latency on failure to understand how long failed calls take.
        latency.record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
