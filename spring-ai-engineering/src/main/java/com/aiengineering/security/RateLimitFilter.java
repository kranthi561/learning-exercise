package com.aiengineering.security;

import com.aiengineering.config.RateLimitProperties;
import com.aiengineering.web.dto.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Lua script executed atomically inside Redis.
    // INCR increments (or creates) the counter key; EXPIRE sets TTL only on the
    // first call (count == 1) so the window resets naturally after windowSeconds.
    private static final RedisScript<Long> INCREMENT_SCRIPT = RedisScript.of(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            return count
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        log.debug("shouldNotFilter: uri={}", request.getRequestURI());
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {
        log.debug("doFilterInternal: method={}, uri={}", request.getMethod(), request.getRequestURI());
        String path = request.getRequestURI();
        RateLimitConfig config = resolveConfig(path);
        String identifier = resolveIdentifier(request, path);
        String redisKey = "rl:" + config.category() + ":" + identifier;

        Long count = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(redisKey),
                String.valueOf(config.windowSeconds()));

        long current = count != null ? count : 1L;
        long remaining = Math.max(0L, config.limit() - current);

        response.setHeader("X-RateLimit-Limit", String.valueOf(config.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Window", config.windowSeconds() + "s");

        if (current > config.limit()) {
            log.warn("Rate limit exceeded: key={}, count={}, limit={}", redisKey, current, config.limit());
            response.setHeader("Retry-After", String.valueOf(config.windowSeconds()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            var body = ErrorResponse.of(
                    429,
                    "Too Many Requests",
                    "Rate limit exceeded. Retry after " + config.windowSeconds() + "s.",
                    List.of());
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        chain.doFilter(request, response);
    }

    private String resolveIdentifier(HttpServletRequest request, String path) {
        log.debug("resolveIdentifier: path={}", path);
        if (!path.startsWith("/api/v1/auth")) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                return "user:" + principal.id();
            }
        }
        return "ip:" + clientIp(request);
    }

    private RateLimitConfig resolveConfig(String path) {
        log.debug("resolveConfig: path={}", path);
        if (path.startsWith("/api/v1/auth")) {
            return new RateLimitConfig("auth", properties.getAuthRequestsPerMinute(), properties.getWindowSeconds());
        }
        if (path.matches(".*/sessions/[^/]+/messages.*")) {
            return new RateLimitConfig("chat", properties.getChatRequestsPerMinute(), properties.getWindowSeconds());
        }
        return new RateLimitConfig("api", properties.getApiRequestsPerMinute(), properties.getWindowSeconds());
    }

    private String clientIp(HttpServletRequest request) {
        log.debug("clientIp: remoteAddr={}", request.getRemoteAddr());
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record RateLimitConfig(String category, int limit, int windowSeconds) {}
}
