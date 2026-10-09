package com.example.quizlet.entity;

/**
 * Quality score the user gives when reviewing a card (Anki-style 1–4).
 * Each level carries its SM-2 ease-factor adjustment.
 */
public enum ReviewQuality {
    AGAIN(1, -0.20),  // forgot completely
    HARD(2, -0.15),   // recalled with difficulty
    GOOD(3, 0.00),    // recalled correctly
    EASY(4, +0.10);   // recalled instantly

    private final int score;
    private final double easeDelta;

    ReviewQuality(int score, double easeDelta) {
        this.score = score;
        this.easeDelta = easeDelta;
    }

    public int getScore() {
        return score;
    }

    public double getEaseDelta() {
        return easeDelta;
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
