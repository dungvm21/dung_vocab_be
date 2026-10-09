package com.example.quizlet.entity;

/**
 * SRS lifecycle of a card for one user.
 * LEARNING  = intra-session retry cycle (next review < 1 day away)
 * REVIEW    = graduated to day-scale intervals
 * MASTERED  = interval reached the mastery threshold (21 days)
 */
public enum ReviewStatus {
    NEW,
    LEARNING,
    REVIEW,
    MASTERED
}
