package com.example.quizlet.entity;

import com.example.quizlet.srs.SrsSchedule;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * SRS scheduling state for one card and one user (modified SuperMemo SM-2).
 * Independent of {@link CardProgress} (the MCQ state machine).
 *
 * Persistence-only state holder: HOW the next schedule is computed lives in
 * {@code com.example.quizlet.srs.ReviewSchedulingPolicy} implementations
 * (strategy pattern), not here.
 *
 * Interval semantics:
 *  - interval_days = 0  → next review is minutes away (LEARNING retry cycle)
 *  - interval_days >= 1 → graduated to day-scale REVIEW
 *  - interval_days >= policy's mastery threshold (21) → MASTERED
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
    private double easeFactor = 2.5;

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

    /** Applies a schedule computed by a {@code ReviewSchedulingPolicy}. */
    public void applySchedule(SrsSchedule schedule) {
        this.intervalDays = schedule.intervalDays();
        this.repetition = schedule.repetition();
        this.easeFactor = schedule.easeFactor();
        this.nextReviewDate = schedule.nextReviewDate();
        this.status = schedule.status();
    }
}
