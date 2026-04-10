package com.aiengineering.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        log.debug("doFilterInternal: method={}, uri={}", request.getMethod(), request.getRequestURI());
        String header = request.getHeader("Authorization");
        // If there is no Bearer token, skip authentication entirely —
        // public endpoints are allowed through; protected ones will be rejected
        // later by Spring Security's AuthorizationFilter.
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        // Strip the "Bearer " prefix (7 chars) and remove surrounding whitespace.
        String token = header.substring(7).trim();
        try {
            // Validate the token signature and expiry; extract the claims payload.
            Claims claims = jwtService.parseClaims(token);
            // The subject was set to the user's numeric ID in JwtService.createToken().
            Long userId = Long.parseLong(claims.getSubject());
            String email = claims.get("email", String.class);
            var principal = new UserPrincipal(userId, email);
            var authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            // Attach request metadata (IP, session ID) to the authentication object —
            // useful for auditing and Spring Security's built-in event system.
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            // Store the authentication in the thread-local SecurityContext so downstream
            // filters, interceptors, and services can call SecurityUtils.requireCurrentUser().
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("doFilterInternal: authenticated userId={}, email={}", userId, email);
        } catch (JwtException | IllegalArgumentException ex) {
            // Token is invalid — clear any partial context and let Spring Security
            // reject the request with 401 for protected endpoints.
            log.debug("doFilterInternal: invalid token — {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}
