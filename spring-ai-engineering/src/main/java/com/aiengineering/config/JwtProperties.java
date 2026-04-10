package com.aiengineering.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Binds all keys under 'app.security.jwt' in application.yml to this record's
// fields at startup — no manual Environment lookups needed.
@ConfigurationProperties(prefix = "app.security.jwt")

// Triggers Bean Validation on this properties class so the app fails
// fast at startup if the YAML values are missing or invalid.
@Validated
public record JwtProperties(
        // @NotBlank ensures the secret is provided and not empty —
        // a missing secret would allow unsigned tokens to be accepted.
        @NotBlank String secret,

        // @Min(60) rejects expiration values shorter than 60 seconds
        // to prevent accidentally issuing immediately-expired tokens.
        @Min(60) long expirationSeconds) {}
