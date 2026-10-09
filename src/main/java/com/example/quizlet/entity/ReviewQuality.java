package com.example.quizlet.entity;

/**
 * Quality score the user gives when reviewing a card (Anki-style 1–4).
 * How each level affects the schedule is decided by the active
 * {@code com.example.quizlet.srs.ReviewSchedulingPolicy}, not here.
 */
public enum ReviewQuality {
    AGAIN(1),  // forgot completely
    HARD(2),   // recalled with difficulty
    GOOD(3),   // recalled correctly
    EASY(4);   // recalled instantly

    private final int score;

    ReviewQuality(int score) {
        this.score = score;
    }

    public int getScore() {
        return score;
    }

    /** Maps a raw 1–4 score from the API to a quality level; unknown scores fail validation upstream. */
    public static ReviewQuality fromScore(int score) {
        for (ReviewQuality q : values()) {
            if (q.score == score) {
                return q;
            }
        }
        throw new IllegalArgumentException("Invalid review quality: " + score);
    }
}
