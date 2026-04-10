package com.aiengineering.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// Utility class — not a Spring bean, just a static helper.
// final prevents subclassing; the private constructor prevents instantiation.
public final class SecurityUtils {

    // Static logger — @Slf4j cannot be used on a class with only static methods,
    // so we declare it manually via LoggerFactory.
    private static final Logger log = LoggerFactory.getLogger(SecurityUtils.class);

    private SecurityUtils() {}

    // Retrieves the currently authenticated user from Spring Security's thread-local context.
    // Throws IllegalStateException (caught by GlobalExceptionHandler → 401) if called
    // on an unauthenticated request, acting as a last-resort guard in service/controller code.
    public static UserPrincipal requireCurrentUser() {
        log.debug("requireCurrentUser: resolving principal from SecurityContext");
        // SecurityContextHolder stores the Authentication for the current request thread.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Pattern-matching instanceof: checks type and binds in one step.
        // If authentication is null or the principal isn't a UserPrincipal
        // (e.g. anonymous user), we reject the call immediately.
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("Unauthenticated");
        }
        return principal;
    }
}
