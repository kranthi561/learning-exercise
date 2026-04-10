package com.aiengineering.web.exception;

// Thrown when a requested entity (user, session, message) does not exist in the database.
// Extends RuntimeException so it doesn't need to be declared in method signatures —
// GlobalExceptionHandler catches it and maps it to HTTP 404.
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
