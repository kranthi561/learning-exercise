package com.uuidservice.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Pre-generates UUID pool so HTTP requests never wait on generation.
 * Refills asynchronously when pool drops below threshold.
 *
 * Pool capacity: 50,000 IDs
 * Refill threshold: 10,000 IDs remaining
 * Refill batch: 20,000 IDs at a time
 *
 * Node identity is read from:
 *   UUID_DATACENTER_ID env var (default 1)
 *   UUID_WORKER_ID     env var (default 0)
 * Each node in a multi-node deployment must have a unique (datacenter, worker) pair.
 *
 * NOTE: Not registered as a Spring bean (@Component removed).
 * Wire this manually if throughput exceeds ~100K/s and the pool becomes necessary.
 */
public class UUIDPool {

    private static final Logger log = LoggerFactory.getLogger(UUIDPool.class);

    private static final int POOL_CAPACITY      = 50_000;
    private static final int REFILL_THRESHOLD   = 10_000;
    private static final int REFILL_BATCH_SIZE  = 20_000;

    private final BlockingQueue<String> pool;
    private final CustomUUIDGenerator   generator;

    public UUIDPool(
            @Value("${uuid.datacenter-id:1}") long datacenterId,
            @Value("${uuid.worker-id:0}")     long workerId) {
        log.info("UUID node identity — datacenter={}, worker={}", datacenterId, workerId);
        this.pool      = new ArrayBlockingQueue<>(POOL_CAPACITY);
        this.generator = new CustomUUIDGenerator(datacenterId, workerId);
        prefill();
    }

    /**
     * Get a UUID instantly from the pool.
     * Falls back to direct generation if pool is temporarily empty.
     */
    public String acquire() {
        String id = pool.poll();
        if (id == null) {
            log.warn("Pool exhausted — generating directly (consider increasing pool size)");
            id = generator.generate();
        }
        return id;
    }

    /**
     * Get multiple UUIDs at once
     */
    public String[] acquireBatch(int count) {
        String[] ids = new String[count];
        for (int i = 0; i < count; i++) {
            ids[i] = acquire();
        }
        return ids;
    }

    public int available() {
        return pool.size();
    }

    // ── Refill logic ────────────────────────────────────────────────────────

    /**
     * Check every 50ms — refill if below threshold.
     * Requires @EnableScheduling and this class registered as a Spring bean.
     */
    @Scheduled(fixedDelay = 50)
    public void refillIfNeeded() {
        if (pool.size() < REFILL_THRESHOLD) {
            refill(REFILL_BATCH_SIZE);
        }
    }

    private void prefill() {
        log.info("Pre-filling UUID pool with {} IDs...", POOL_CAPACITY / 2);
        refill(POOL_CAPACITY / 2);
        log.info("UUID pool ready. Size: {}", pool.size());
    }

    private void refill(int count) {
        int added = 0;
        for (int i = 0; i < count; i++) {
            String id = generator.generate();
            if (pool.offer(id)) {
                added++;
            } else {
                break;
            }
        }
        if (added > 0) {
            log.debug("Refilled pool with {} IDs. Total: {}", added, pool.size());
        }
    }
}
