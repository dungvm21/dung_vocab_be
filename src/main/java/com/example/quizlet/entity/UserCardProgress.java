package com.example.quizlet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * SRS scheduling state for one card and one user (modified SuperMemo SM-2).
 * Independent of {@link CardProgress} (the MCQ state machine).
 *
 * Interval semantics:
 *  - interval_days = 0  → next review is minutes away (LEARNING retry cycle)
 *  - interval_days >= 1 → graduated to day-scale REVIEW
 *  - interval_days >= MASTERED_INTERVAL_DAYS (21) → MASTERED
 */
@Entity
@Table(name = "user_card_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_card_progress", columnNames = {"user_id", "card_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCardProgress {

    /** SM-2 constants. */
    public static final double INITIAL_EASE = 2.5;
    public static final double MIN_EASE = 1.3;
    public static final double MAX_EASE = 2.8;
    public static final int MAX_INTERVAL_DAYS = 365;
    public static final int MASTERED_INTERVAL_DAYS = 21;
    /** Relearn delay after a lapse (AGAIN). */
    public static final int RELEARN_MINUTES = 10;
    /** HARD keeps some growth but much slower than the ease factor would give. */
    public static final double HARD_MULTIPLIER = 1.2;
    /** EASY stretches the interval further on top of the ease factor. */
    public static final double EASY_BONUS = 1.3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    /** Days until the next review. 0 = retry in minutes (learning cycle). */
    @Column(name = "interval_days", nullable = false)
    @Builder.Default
    private int intervalDays = 0;

    /** Consecutive successful (GOOD/EASY) reviews since the last lapse. */
    @Column(nullable = false)
    @Builder.Default
    private int repetition = 0;

    @Column(name = "ease_factor", nullable = false)
    @Builder.Default
    private double easeFactor = INITIAL_EASE;

    /** The card is due for review when this instant <= now. */
    @Column(name = "next_review_date", nullable = false)
    private Instant nextReviewDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.NEW;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (nextReviewDate == null) {
            nextReviewDate = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isDue(Instant now) {
        return !nextReviewDate.isAfter(now);
    }

    /**
     * Applies a review and computes the next schedule (modified SM-2).
     *
     * AGAIN (1): lapse        → repetition 0, relearn in RELEARN_MINUTES, ease −0.20
     * HARD  (2): struggle     → interval × HARD_MULTIPLIER (or relearn on first sight), ease −0.15
     * GOOD  (3): correct      → interval × easeFactor, ease unchanged
     * EASY  (4): instant recall → interval × easeFactor × EASY_BONUS, ease +0.10
     */
    public void applyReview(ReviewQuality quality, Instant now) {
        switch (quality) {
            case AGAIN -> {
                repetition = 0;
                intervalDays = 0;
                easeFactor = clampEase(easeFactor + quality.getEaseDelta());
                status = ReviewStatus.LEARNING;
                nextReviewDate = now.plus(RELEARN_MINUTES, ChronoUnit.MINUTES);
            }
            case HARD -> {
                easeFactor = clampEase(easeFactor + quality.getEaseDelta());
                if (status == ReviewStatus.NEW || repetition == 0) {
                    // First sight not yet recalled cleanly — short relearn cycle.
                    intervalDays = 0;
                    status = ReviewStatus.LEARNING;
                    nextReviewDate = now.plus(RELEARN_MINUTES, ChronoUnit.MINUTES);
                } else {
                    intervalDays = capInterval((int) Math.round(intervalDays * HARD_MULTIPLIER));
                    status = statusFor(intervalDays);
                    nextReviewDate = now.plus(intervalDays, ChronoUnit.DAYS);
                }
            }
            case GOOD, EASY -> {
                repetition++;
                if (repetition == 1) {
                    intervalDays = quality == ReviewQuality.EASY ? 3 : 1;
                } else {
                    double multiplier = easeFactor * (quality == ReviewQuality.EASY ? EASY_BONUS : 1.0);
                    intervalDays = capInterval((int) Math.max(1, Math.round(intervalDays * multiplier)));
                }
                if (quality == ReviewQuality.EASY) {
                    easeFactor = clampEase(easeFactor + quality.getEaseDelta());
                }
                status = statusFor(intervalDays);
                nextReviewDate = now.plus(intervalDays, ChronoUnit.DAYS);
            }
        }
    }

    private static double clampEase(double ease) {
        return Math.max(MIN_EASE, Math.min(MAX_EASE, ease));
    }

    private static int capInterval(int days) {
        return Math.min(Math.max(days, 1), MAX_INTERVAL_DAYS);
    }

    private static ReviewStatus statusFor(int intervalDays) {
        return intervalDays >= MASTERED_INTERVAL_DAYS ? ReviewStatus.MASTERED : ReviewStatus.REVIEW;
    }
}
