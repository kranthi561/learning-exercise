package com.aiengineering.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class AgentMetrics {

    private final Counter callsSuccess;
    private final Counter callsFailure;
    private final Timer latency;
    private final DistributionSummary tokenUsage;

    public AgentMetrics(MeterRegistry registry) {
        this.callsSuccess = registry.counter("agent.calls", "outcome", "success");
        this.callsFailure = registry.counter("agent.calls", "outcome", "failure");
        this.latency = registry.timer("agent.latency");
        this.tokenUsage = DistributionSummary.builder("agent.tokens.total")
                .description("Total tokens reported by the model when available")
                .register(registry);
    }

    public void recordSuccess(long durationNanos, Integer totalTokens) {
        callsSuccess.increment();
        latency.record(durationNanos, TimeUnit.NANOSECONDS);
        if (totalTokens != null && totalTokens > 0) {
            tokenUsage.record(totalTokens);
        }
    }

    public void recordFailure(long durationNanos) {
        callsFailure.increment();
        latency.record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
