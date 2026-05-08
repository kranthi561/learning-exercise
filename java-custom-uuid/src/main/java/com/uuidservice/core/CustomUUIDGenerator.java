package com.uuidservice.core;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;

/**
 * Custom UUID Generator — 128-bit, guaranteed unique at 10K+/s
 *
 * Bit Layout (high 64 bits):
 * ┌────────────────────────────────────────────────────────────────┐
 * │ timestamp(42b) │ datacenter(5b) │ worker(5b) │ sequence(12b)  │
 * └────────────────────────────────────────────────────────────────┘
 *
 * Low 64 bits: SecureRandom entropy
 *
 * Capacity: 4096 unique IDs per millisecond per node = ~4M/s single node
 */
public class CustomUUIDGenerator {

    // Custom epoch: 2024-01-01T00:00:00Z (saves bits vs 1970)
    private static final long CUSTOM_EPOCH    = 1704067200000L;

    // Bit lengths
    private static final int  TIMESTAMP_BITS  = 42;
    private static final int  DATACENTER_BITS = 5;
    private static final int  WORKER_BITS     = 5;
    private static final int  SEQUENCE_BITS   = 12;

    // Bit shifts
    private static final int  SEQUENCE_SHIFT    = 0;
    private static final int  WORKER_SHIFT      = SEQUENCE_BITS;
    private static final int  DATACENTER_SHIFT  = SEQUENCE_BITS + WORKER_BITS;
    private static final int  TIMESTAMP_SHIFT   = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;

    // Max values (bitmasks)
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_BITS);  // 31
    private static final long MAX_WORKER_ID     = ~(-1L << WORKER_BITS);      // 31
    private static final long MAX_SEQUENCE      = ~(-1L << SEQUENCE_BITS);    // 4095

    private final long datacenterId;
    private final long workerId;
    private final SecureRandom random;

    private long sequence       = 0L;
    private long lastTimestamp  = -1L;

    public CustomUUIDGenerator(long datacenterId, long workerId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0)
            throw new IllegalArgumentException("datacenterId must be 0–" + MAX_DATACENTER_ID);
        if (workerId > MAX_WORKER_ID || workerId < 0)
            throw new IllegalArgumentException("workerId must be 0–" + MAX_WORKER_ID);

        this.datacenterId = datacenterId;
        this.workerId     = workerId;
        this.random       = new SecureRandom();
    }

    /**
     * Auto-detect worker ID from MAC address
     */
    public static CustomUUIDGenerator create(long datacenterId) {
        long workerId = detectWorkerId();
        return new CustomUUIDGenerator(datacenterId, workerId);
    }

    /**
     * Generate a unique 128-bit UUID as formatted hex string
     * Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     */
    public synchronized String generate() {
        long high = buildHighBits();
        long low  = random.nextLong();  // 64-bit entropy for low half
        return format(high, low);
    }

    /**
     * Bulk generation — more efficient for batch requests
     */
    public String[] generateBatch(int count) {
        String[] ids = new String[count];
        for (int i = 0; i < count; i++) {
            ids[i] = generate();
        }
        return ids;
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private synchronized long buildHighBits() {
        long ts = currentMs();

        if (ts < lastTimestamp) {
            // Clock moved backwards — wait for recovery
            long drift = lastTimestamp - ts;
            if (drift <= 5) {
                safeSleep(drift);
                ts = currentMs();
            } else {
                throw new RuntimeException("Clock moved back " + drift + "ms — refusing to generate");
            }
        }

        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted in this ms — block until next ms
                ts = waitNextMs(lastTimestamp);
            }
        } else {
            // New millisecond — reset sequence with small random offset
            // to spread IDs across sequence space (reduces hotspot in DB indexes)
            sequence = random.nextInt(10);
        }

        lastTimestamp = ts;

        long relativeTs = ts - CUSTOM_EPOCH;

        return (relativeTs    << TIMESTAMP_SHIFT)
             | (datacenterId  << DATACENTER_SHIFT)
             | (workerId      << WORKER_SHIFT)
             | (sequence      << SEQUENCE_SHIFT);
    }

    private String format(long high, long low) {
        // %x on a Long uses the two's complement bit pattern (unsigned display)
        String h = String.format("%016x", high);
        String l = String.format("%016x", low);

        // Standard UUID shape: 8-4-4-4-12
        return h.substring(0, 8)  + "-" +
               h.substring(8, 12) + "-" +
               h.substring(12, 16) + "-" +
               l.substring(0, 4)   + "-" +
               l.substring(4, 16);
    }

    private long currentMs() {
        return System.currentTimeMillis();
    }

    private long waitNextMs(long lastTs) {
        long ts = currentMs();
        while (ts <= lastTs) ts = currentMs();
        return ts;
    }

    private void safeSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long detectWorkerId() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                byte[] mac = nic.getHardwareAddress();
                if (mac != null && mac.length >= 2) {
                    return ((mac[mac.length - 1] & 0xFF) + (mac[mac.length - 2] & 0xFF)) % 32;
                }
            }
        } catch (Exception ignored) {}
        return new SecureRandom().nextInt(32);
    }
}
