package com.uuidservice.core;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Two-layer uniqueness tracker:
 *
 * Layer 1 — Bloom Filter (probabilistic, zero-allocation check)
 *   • ~1MB memory for 1M entries at 0.01% false-positive rate
 *   • Instant "definitely not seen" answer
 *
 * Layer 2 — ConcurrentHashMap (exact, authoritative)
 *   • Only queried if Bloom filter says "possibly seen"
 *   • Stores metadata per UUID
 */
@Component
public class UUIDTracker {

    // ── Bloom Filter ─────────────────────────────────────────────────────────

    private static final int BLOOM_SIZE = 8_000_000; // 8M bits = 1MB
    private final BitSet bloomFilter = new BitSet(BLOOM_SIZE);

    // ── Exact store ──────────────────────────────────────────────────────────

    public record UUIDRecord(
        String  uuid,
        long    issuedAt,       // epoch ms
        String  clientIp,
        String  tag,            // optional label
        boolean valid
    ) {}

    private final ConcurrentHashMap<String, UUIDRecord> registry = new ConcurrentHashMap<>();
    private final AtomicLong totalIssued     = new AtomicLong(0);
    private final AtomicLong collisionBlocks = new AtomicLong(0);

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Register a UUID as issued. Returns false if duplicate detected.
     *
     * putIfAbsent is the atomic authority — bloom filter is a pre-check optimization only.
     */
    public boolean register(String uuid, String clientIp, String tag) {
        // Fast path: bloom says "definitely not seen" → skip map lookup
        if (mightExist(uuid)) {
            if (registry.containsKey(uuid)) {
                collisionBlocks.incrementAndGet();
                return false;
            }
        }

        UUIDRecord record = new UUIDRecord(
            uuid,
            Instant.now().toEpochMilli(),
            clientIp != null ? clientIp : "unknown",
            tag != null ? tag : "",
            true
        );
        // Atomic: only one thread wins; the loser gets the existing record back
        UUIDRecord existing = registry.putIfAbsent(uuid, record);
        if (existing != null) {
            collisionBlocks.incrementAndGet();
            return false;
        }

        bloomAdd(uuid);
        totalIssued.incrementAndGet();
        return true;
    }

    /**
     * Look up a UUID
     */
    public Optional<UUIDRecord> lookup(String uuid) {
        if (!mightExist(uuid)) return Optional.empty();
        return Optional.ofNullable(registry.get(uuid));
    }

    /**
     * Invalidate (soft-delete) a UUID
     */
    public boolean invalidate(String uuid) {
        UUIDRecord existing = registry.get(uuid);
        if (existing == null) return false;

        UUIDRecord invalidated = new UUIDRecord(
            existing.uuid(), existing.issuedAt(),
            existing.clientIp(), existing.tag(), false
        );
        registry.put(uuid, invalidated);
        return true;
    }

    public long totalIssued()     { return totalIssued.get(); }
    public long collisionBlocks() { return collisionBlocks.get(); }
    public int  registrySize()    { return registry.size(); }

    // ── Bloom Filter internals ───────────────────────────────────────────────

    private boolean mightExist(String uuid) {
        int[] hashes = bloomHashes(uuid);
        synchronized (bloomFilter) {
            for (int h : hashes) {
                if (!bloomFilter.get(Math.abs(h) % BLOOM_SIZE)) return false;
            }
        }
        return true;
    }

    private void bloomAdd(String uuid) {
        int[] hashes = bloomHashes(uuid);
        synchronized (bloomFilter) {
            for (int h : hashes) {
                bloomFilter.set(Math.abs(h) % BLOOM_SIZE);
            }
        }
    }

    /**
     * Three independent hash functions using FNV-like mixing
     */
    private int[] bloomHashes(String uuid) {
        int h1 = fnv1a(uuid, 0x811c9dc5);
        int h2 = fnv1a(uuid, 0x01000193);
        int h3 = h1 ^ (h2 << 13) ^ (h2 >>> 19);
        return new int[]{h1, h2, h3};
    }

    private int fnv1a(String s, int seed) {
        int hash = seed;
        for (char c : s.toCharArray()) {
            hash ^= c;
            hash *= 0x01000193;
        }
        return hash;
    }
}
