package com.aiengineering.web.exception;

import lombok.Getter;

// Thrown when a caller exceeds the configured request rate for their tier.
// Extends RuntimeException — no checked-exception boilerplate needed.
// GlobalExceptionHandler catches this and returns HTTP 429 with a Retry-After header.
@Getter // Lombok: generates getRetryAfterSeconds() so the handler can read it.
public class RateLimitExceededException extends RuntimeException {

    // How many seconds the client should wait before retrying —
    // matches the rate-limit window duration configured in RateLimitProperties.
    private final int retryAfterSeconds;

    public RateLimitExceededException(int retryAfterSeconds) {
        super("Rate limit exceeded. Retry after " + retryAfterSeconds + "s.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
