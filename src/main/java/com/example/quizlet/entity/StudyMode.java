package com.example.quizlet.entity;

/**
 * Study mode of an answer attempt. Extend when new modes (FLASHCARD, WRITING) ship —
 * the V5 CHECK constraint must be widened in a new migration at the same time.
 */
public enum StudyMode {
    MULTIPLE_CHOICE
}
