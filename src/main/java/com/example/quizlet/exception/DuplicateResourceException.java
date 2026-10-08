package com.example.quizlet.exception;

/**
 * Thrown on uniqueness violations such as a duplicate username or email.
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
