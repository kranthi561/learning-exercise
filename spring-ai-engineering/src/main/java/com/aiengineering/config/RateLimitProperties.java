package com.aiengineering.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Registers this class as a Spring bean so it can be injected into RateLimitFilter.
@Component

// Binds all keys under 'app.rate-limit' from application.yml to this class's fields.
// Values can be overridden per-environment via RATE_LIMIT_* environment variables.
@ConfigurationProperties(prefix = "app.rate-limit")

// Lombok: generates getters so @ConfigurationProperties can read the bound values.
@Getter

// Lombok: generates setters so Spring Boot can write the YAML values into the fields
// during property binding (ConfigurationProperties requires writable fields).
@Setter
public class RateLimitProperties {

    /** Max login/register attempts per IP per window */
    private int authRequestsPerMinute = 10;

    /** Max general API calls per authenticated user per window */
    private int apiRequestsPerMinute = 60;

    /** Max chat message sends per authenticated user per window (AI calls are expensive) */
    private int chatRequestsPerMinute = 20;

    /** Sliding-window duration in seconds */
    private int windowSeconds = 60;
}
