package com.aiengineering.web.interceptor;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

// Order(1): runs before RateLimitFilter(@Order(2)) so every log line produced by
// downstream filters and the application already carries [requestId] in MDC.
@Slf4j
@Component
@Order(1)
public class LoggingInterceptor extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_KEY           = "requestId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        log.debug("request: method={}, uri={}", request.getMethod(), request.getRequestURI());
        long startTime = System.currentTimeMillis();

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("{} {} -> {} | {}ms",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            // Always clear MDC — the servlet container reuses threads across requests.
            MDC.remove(MDC_KEY);
        }
    }
}
