package com.example.quizlet.dto.review;

/**
 * Result of enrolling a whole deck into the SRS review queue. Idempotent —
 * cards already enrolled are counted but not modified.
 */
public record EnrollmentResponse(
        Long deckId,
        int totalCards,
        int newlyEnrolled,
        int alreadyEnrolled
) {
}
