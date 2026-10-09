package com.example.quizlet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One recorded answer by a user during study. Powers review lists
 * ("my mistakes in deck X") and future SRS scheduling.
 */
@Entity
@Table(name = "study_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    /** Denormalized for fast per-deck history queries. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyMode mode;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    /** Which option the user picked (MCQ). Null = skipped. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_card_id")
    private Card selectedCard;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private Instant answeredAt;

    @PrePersist
    void onCreate() {
        this.answeredAt = Instant.now();
    }
}
