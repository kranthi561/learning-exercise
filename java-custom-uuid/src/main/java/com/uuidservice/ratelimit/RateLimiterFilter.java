package com.uuidservice.ratelimit;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Bucket Rate Limiter
 *
 * Global:  10,000 requests/second
 * Per-IP:  1,000 requests/second
 *
 * Token bucket refills continuously — burst-friendly.
 */
@Component
public class RateLimiterFilter implements Filter {

    // Global bucket
    private static final long GLOBAL_CAPACITY     = 10_000;
    private static final long GLOBAL_REFILL_RATE  = 10_000; // tokens/sec

    // Per-IP bucket
    private static final long IP_CAPACITY         = 1_000;
    private static final long IP_REFILL_RATE      = 1_000;  // tokens/sec

    private final TokenBucket globalBucket = new TokenBucket(GLOBAL_CAPACITY, GLOBAL_REFILL_RATE);
    private final ConcurrentHashMap<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Skip non-UUID endpoints
        if (!request.getRequestURI().startsWith("/api/v1/uuid")) {
            chain.doFilter(req, res);
            return;
        }

        String ip = extractIp(request);

        // Check global limit first
        if (!globalBucket.tryConsume()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Global rate limit exceeded (10K/s)\",\"retryAfterMs\":100}");
            return;
        }

        // Check per-IP limit
        TokenBucket ipBucket = ipBuckets.computeIfAbsent(ip,
            k -> new TokenBucket(IP_CAPACITY, IP_REFILL_RATE));

        if (!ipBucket.tryConsume()) {
            // Refund global token since IP was rejected
            globalBucket.refund();
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"IP rate limit exceeded (1K/s per IP)\",\"ip\":\"" + ip + "\",\"retryAfterMs\":1}");
            return;
        }

        // Add rate-limit headers
        response.setHeader("X-RateLimit-Remaining", String.valueOf(globalBucket.available()));
        response.setHeader("X-RateLimit-IP-Remaining", String.valueOf(ipBucket.available()));

        chain.doFilter(req, res);
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ── Token Bucket ─────────────────────────────────────────────────────────

    static class TokenBucket {
        private final long capacity;
        private final long refillRate;      // tokens per second
        private final AtomicLong tokens;
        private volatile long lastRefillNs;

        TokenBucket(long capacity, long refillRate) {
            this.capacity    = capacity;
            this.refillRate  = refillRate;
            this.tokens      = new AtomicLong(capacity);
            this.lastRefillNs = System.nanoTime();
        }

        boolean tryConsume() {
            refill();
            while (true) {
                long current = tokens.get();
                if (current <= 0) return false;
                if (tokens.compareAndSet(current, current - 1)) return true;
            }
        }

        void refund() {
            tokens.updateAndGet(t -> Math.min(capacity, t + 1));
        }

        long available() {
            refill();
            return Math.max(0, tokens.get());
        }

        private void refill() {
            long now       = System.nanoTime();
            long elapsedNs = now - lastRefillNs;
            long toAdd     = (elapsedNs * refillRate) / 1_000_000_000L;

            if (toAdd > 0) {
                lastRefillNs = now;
                tokens.updateAndGet(t -> Math.min(capacity, t + toAdd));
            }
        }
    }
}
