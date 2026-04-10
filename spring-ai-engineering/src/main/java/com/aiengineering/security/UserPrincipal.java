package com.aiengineering.security;

// Immutable value object that represents the authenticated user inside the application.
// Stored as the principal in Spring Security's Authentication object after JWT validation.
// Using a record keeps it concise — Java generates equals, hashCode, toString, and accessors.
public record UserPrincipal(
        Long id,      // The user's database primary key — used to scope DB queries to the owner
        String email  // Carried for logging/debugging without an extra DB lookup
) {}
