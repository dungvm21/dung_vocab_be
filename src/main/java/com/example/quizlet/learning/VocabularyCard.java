package com.example.quizlet.learning;

/**
 * A vocabulary word card with learning progress state.
 *
 * Core transition rules:
 *  - starts NOT_LEARNED, consecutiveCorrect = 0
 *  - correct #1  -> STILL_LEARNING (count = 1)
 *  - correct #2  -> MASTERED       (count = 2)
 *  - wrong       -> count reset to 0;
 *                   MASTERED demotes to STILL_LEARNING,
 *                   STILL_LEARNING stays STILL_LEARNING,
 *                   NOT_LEARNED stays NOT_LEARNED (never answered correctly yet)
 */
public class VocabularyCard {

    /** Consecutive correct answers needed to reach MASTERED. */
    public static final int MASTERY_THRESHOLD = 2;

    private final Long id;
    private final String front;
    private final String back;
    private boolean starred;
    private WordState state = WordState.NOT_LEARNED;
    private int consecutiveCorrect = 0;

    public VocabularyCard(Long id, String front, String back) {
        this.id = id;
        this.front = front;
        this.back = back;
    }

    /** User answered this word correctly. */
    public void recordCorrect() {
        consecutiveCorrect++;
        switch (state) {
            case NOT_LEARNED -> state = WordState.STILL_LEARNING;
            case STILL_LEARNING -> {
                if (consecutiveCorrect >= MASTERY_THRESHOLD) {
                    state = WordState.MASTERED;
                }
            }
            case MASTERED -> { /* already mastered, nothing to promote */ }
        }
    }

    /** User answered this word incorrectly. */
    public void recordIncorrect() {
        consecutiveCorrect = 0;
        if (state == WordState.MASTERED) {
            state = WordState.STILL_LEARNING;
        }
    }

    public void toggleStar() {
        starred = !starred;
    }

    public Long getId() {
        return id;
    }

    public String getFront() {
        return front;
    }

    public String getBack() {
        return back;
    }

    public boolean isStarred() {
        return starred;
    }

    public WordState getState() {
        return state;
    }

    public int getConsecutiveCorrect() {
        return consecutiveCorrect;
    }

    @Override
    public String toString() {
        return "VocabularyCard[id=%d, front=%s, back=%s, state=%s, streak=%d, starred=%s]".formatted(
                id, front, back, state, consecutiveCorrect, starred);
    }
}
