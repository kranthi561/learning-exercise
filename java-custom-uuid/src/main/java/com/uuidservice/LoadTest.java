package com.uuidservice;

import com.uuidservice.core.CustomUUIDGenerator;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone load test — run directly (no Spring context needed)
 *
 * Tests:
 * 1. Raw generation throughput
 * 2. Uniqueness across 1M IDs
 * 3. Concurrent generation correctness
 */
public class LoadTest {

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════");
        System.out.println("  UUID Service — Load Test");
        System.out.println("═══════════════════════════════════════\n");

        testRawThroughput();
        testUniqueness();
        testConcurrentUniqueness();
    }

    // ── Test 1: Raw Generation Speed ─────────────────────────────────────────

    static void testRawThroughput() {
        System.out.println("▶ Test 1: Raw generation throughput");
        CustomUUIDGenerator gen = new CustomUUIDGenerator(1, 1);

        int count = 1_000_000;
        long start = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            gen.generate();
        }

        long elapsed = System.currentTimeMillis() - start;
        long perSec  = (count * 1000L) / elapsed;

        System.out.printf("  Generated: %,d IDs%n", count);
        System.out.printf("  Time:      %,d ms%n", elapsed);
        System.out.printf("  Rate:      %,d IDs/sec%n", perSec);
        System.out.printf("  Result:    %s%n%n",
            perSec >= 10_000 ? "✅ PASS (≥10K/s)" : "❌ FAIL");
    }

    // ── Test 2: Uniqueness across 1M IDs ────────────────────────────────────

    static void testUniqueness() {
        System.out.println("▶ Test 2: Uniqueness check (1M IDs)");
        CustomUUIDGenerator gen = new CustomUUIDGenerator(1, 2);

        Set<String> seen = ConcurrentHashMap.newKeySet(1_000_000);
        int count        = 1_000_000;
        int collisions   = 0;

        for (int i = 0; i < count; i++) {
            String id = gen.generate();
            if (!seen.add(id)) collisions++;
        }

        System.out.printf("  Generated:  %,d IDs%n", count);
        System.out.printf("  Unique:     %,d IDs%n", seen.size());
        System.out.printf("  Collisions: %d%n", collisions);
        System.out.printf("  Result:     %s%n%n",
            collisions == 0 ? "✅ PASS (zero collisions)" : "❌ FAIL (" + collisions + " collisions)");
    }

    // ── Test 3: Concurrent uniqueness ───────────────────────────────────────

    static void testConcurrentUniqueness() throws Exception {
        System.out.println("▶ Test 3: Concurrent generation (16 threads × 100K IDs)");

        int threads   = 16;
        int perThread = 100_000;
        int total     = threads * perThread;

        Set<String> seen          = ConcurrentHashMap.newKeySet(total);
        AtomicLong  collisions    = new AtomicLong(0);
        ExecutorService pool      = Executors.newFixedThreadPool(threads);
        CountDownLatch latch      = new CountDownLatch(threads);

        // Use separate generator per thread (same datacenter, different worker IDs)
        long start = System.currentTimeMillis();

        for (int t = 0; t < threads; t++) {
            final int workerId = t;
            pool.submit(() -> {
                CustomUUIDGenerator gen = new CustomUUIDGenerator(1, workerId);
                for (int i = 0; i < perThread; i++) {
                    String id = gen.generate();
                    if (!seen.add(id)) collisions.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        long elapsed = System.currentTimeMillis() - start;
        long perSec  = (total * 1000L) / elapsed;

        System.out.printf("  Threads:    %d%n", threads);
        System.out.printf("  Total IDs:  %,d%n", total);
        System.out.printf("  Unique:     %,d%n", seen.size());
        System.out.printf("  Collisions: %d%n", collisions.get());
        System.out.printf("  Time:       %,d ms%n", elapsed);
        System.out.printf("  Rate:       %,d IDs/sec%n", perSec);
        System.out.printf("  Result:     %s%n%n",
            collisions.get() == 0 ? "✅ PASS" : "❌ FAIL");

        System.out.println("═══════════════════════════════════════");
        System.out.println("  All tests complete.");
        System.out.println("═══════════════════════════════════════");
    }
}
