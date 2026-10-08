package com.example.quizlet.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single flashcard: a term and its definition, optionally with
 * a hint or usage example. Belongs to exactly one deck.
 */
@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String term;

    @Column(nullable = false, length = 2000)
    private String definition;

    /** Optional hint or usage example shown during study. */
    @Column(length = 1000)
    private String hint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;
}
