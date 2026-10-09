package com.example.quizlet.entity;

import com.example.quizlet.learning.WordState;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Per-user learning progress for a single card.
 * One row per (user, card); a missing row means the card is implicitly NOT_LEARNED.
 *
 * State machine (mirrors {@link com.example.quizlet.learning.VocabularyCard}):
 *  - correct #1  -> STILL_LEARNING (streak 1)
 *  - correct #2  -> MASTERED       (streak 2)
 *  - wrong       -> streak 0; MASTERED demotes to STILL_LEARNING, otherwise stays
 */
@Entity
@Table(name = "card_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uq_progress_user_card", columnNames = {"user_id", "card_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardProgress {

    /** Consecutive correct answers needed to reach MASTERED. Same rule as learning.VocabularyCard. */
    public static final int MASTERY_THRESHOLD = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WordState state = WordState.NOT_LEARNED;

    @Column(name = "consecutive_correct", nullable = false)
    @Builder.Default
    private int consecutiveCorrect = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean starred = false;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** User answered this card correctly. */
    public void recordCorrect() {
        consecutiveCorrect++;
        switch (state) {
            case NOT_LEARNED -> state = WordState.STILL_LEARNING;
            case STILL_LEARNING -> {
                if (consecutiveCorrect >= MASTERY_THRESHOLD) {
                    state = WordState.MASTERED;
                }
            }
            case MASTERED -> { /* stays mastered */ }
        }
    }

    /** User answered this card incorrectly. */
    public void recordIncorrect() {
        consecutiveCorrect = 0;
        if (state == WordState.MASTERED) {
            state = WordState.STILL_LEARNING;
        }
    }

    public void toggleStar() {
        starred = !starred;
    }
}
