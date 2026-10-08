package com.example.quizlet.learning;

/**
 * Learning state of a single vocabulary word.
 */
public enum WordState {
    /** Initial state. No correct answers yet (or never attempted). */
    NOT_LEARNED,
    /** User is making progress: at least one correct answer, but streak below mastery threshold. */
    STILL_LEARNING,
    /** Fully memorized: MASTERY_THRESHOLD consecutive correct answers reached. */
    MASTERED
}
