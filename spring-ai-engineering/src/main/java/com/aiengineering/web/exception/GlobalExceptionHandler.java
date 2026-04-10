package com.aiengineering.web.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aiengineering.web.dto.error.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.debug("handleValidation: {}", ex.getMessage());
        var details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.toList());
        var body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "Validation failed", "Invalid request payload", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.debug("handleNotFound: {}", ex.getMessage());
        var body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "Not found", ex.getMessage(), java.util.List.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.debug("handleBadCredentials: {}", ex.getMessage());
        var body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Invalid credentials", java.util.List.of());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        log.debug("handleRateLimit: retryAfter={}", ex.getRetryAfterSeconds());
        var body = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", ex.getMessage(), java.util.List.of());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.debug("handleAccessDenied: {}", ex.getMessage());
        var body = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage(), java.util.List.of());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("handleIllegalArgument: {}", ex.getMessage());
        var body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "Bad request", ex.getMessage(), java.util.List.of());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.debug("handleIllegalState: {}", ex.getMessage());
        var body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage(), java.util.List.of());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.debug("handleGeneric: {}", ex.getMessage());
        var body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal error",
                "An unexpected error occurred",
                java.util.List.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
