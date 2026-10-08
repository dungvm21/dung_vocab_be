package com.example.quizlet.exception;

/**
 * Thrown when a requested resource does not exist. Mapped to HTTP 404
 * by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super(String.format("%s not found with id=%s", resource, id));
    }
}
