package com.example.quizlet.service.impl;

import com.example.quizlet.dto.review.DueReviewsResponse;
import com.example.quizlet.dto.review.EnrollmentResponse;
import com.example.quizlet.dto.review.ReviewRequest;
import com.example.quizlet.dto.review.ReviewResponse;
import com.example.quizlet.dto.review.SavedReviewResponse;
import com.example.quizlet.entity.Card;
import com.example.quizlet.entity.Deck;
import com.example.quizlet.entity.ReviewQuality;
import com.example.quizlet.entity.ReviewStatus;
import com.example.quizlet.entity.User;
import com.example.quizlet.entity.UserCardProgress;
import com.example.quizlet.exception.ResourceNotFoundException;
import com.example.quizlet.repository.CardRepository;
import com.example.quizlet.repository.UserCardProgressRepository;
import com.example.quizlet.service.DeckService;
import com.example.quizlet.service.ReviewService;
import com.example.quizlet.srs.ReviewSchedulingPolicy;
import com.example.quizlet.srs.SrsSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link ReviewService}: manages the per-user review queue
 * (single card or whole-deck enrollment) and delegates schedule computation
 * to the configured {@link ReviewSchedulingPolicy} (SM-2).
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final int MAX_DUE_BATCH = 100;

    private final UserCardProgressRepository progressRepository;
    private final CardRepository cardRepository;
    private final DeckService deckService;
    private final ReviewSchedulingPolicy schedulingPolicy;

    /** Cards due for review right now for the caller, oldest due first. */
    @Transactional(readOnly = true)
    public DueReviewsResponse getDueReviews(User user, int limit) {
        Instant now = Instant.now();
        int batch = Math.min(Math.max(limit, 1), MAX_DUE_BATCH);
        Pageable pageable = PageRequest.of(0, batch);
        List<UserCardProgress> due = progressRepository.findDue(user.getId(), now, pageable);
        return DueReviewsResponse.of(due, now);
    }

    /**
     * Records one review and reschedules the card via the SRS policy.
     * First review of a card creates its progress row (status NEW).
     */
    @Transactional
    public ReviewResponse submitReview(Long cardId, ReviewRequest request, User user) {
        Card card = getVisibleCard(cardId, user);

        UserCardProgress progress = progressRepository
                .findByUserIdAndCardId(user.getId(), cardId)
                .orElseGet(() -> UserCardProgress.builder()
                        .user(user)
                        .card(card)
                        .build());

        ReviewQuality quality = ReviewQuality.fromScore(request.quality());
        SrsSchedule schedule = schedulingPolicy.scheduleNext(progress, quality, Instant.now());
        progress.applySchedule(schedule);
        // Newly built entities are transient — explicit save required.
        progressRepository.save(progress);

        return ReviewResponse.from(progress, quality.getScore());
    }

    // ---- Review queue enrollment ----

    /**
     * Adds one card to the caller's review queue, due immediately.
     * Idempotent: saving an already-enqueued card returns saved=false, untouched schedule.
     */
    @Transactional
    public SavedReviewResponse saveToReviewList(Long cardId, User user) {
        Card card = getVisibleCard(cardId, user);

        return progressRepository.findByUserIdAndCardId(user.getId(), cardId)
                .<SavedReviewResponse>map(progress -> SavedReviewResponse.of(progress, false))
                .orElseGet(() -> {
                    UserCardProgress progress = progressRepository.save(newRow(user, card));
                    return SavedReviewResponse.of(progress, true);
                });
    }

    /**
     * Enrolls every card of a deck into the review queue (due immediately).
     * Idempotent per card. Works on public decks too — the queue is personal.
     */
    @Transactional
    public EnrollmentResponse enrollDeck(Long deckId, User user) {
        Deck deck = deckService.getViewable(deckId, user);
        List<Card> cards = deck.getCards();
        if (cards.isEmpty()) {
            return new EnrollmentResponse(deckId, 0, 0, 0);
        }

        List<Long> cardIds = cards.stream().map(Card::getId).toList();
        Set<Long> enrolled = new HashSet<>();
        progressRepository.findByUserIdAndCardIdIn(user.getId(), cardIds)
                .forEach(p -> enrolled.add(p.getCard().getId()));

        List<UserCardProgress> newRows = cards.stream()
                .filter(card -> !enrolled.contains(card.getId()))
                .map(card -> newRow(user, card))
                .toList();
        progressRepository.saveAll(newRows);

        return new EnrollmentResponse(deckId, cards.size(), newRows.size(), enrolled.size());
    }

    /**
     * Removes a card from the caller's review queue (SRS state only —
     * the MCQ card_progress record is untouched). No-op if not enqueued.
     */
    @Transactional
    public void removeFromReviewList(Long cardId, User user) {
        getVisibleCard(cardId, user);
        progressRepository.findByUserIdAndCardId(user.getId(), cardId)
                .ifPresent(progressRepository::delete);
    }

    // ---- Helpers ----

    /** Card must exist AND live in a deck the caller can view. */
    private Card getVisibleCard(Long cardId, User user) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", cardId));
        deckService.getViewable(card.getDeck().getId(), user);
        return card;
    }

    private UserCardProgress newRow(User user, Card card) {
        return UserCardProgress.builder()
                .user(user)
                .card(card)
                .status(ReviewStatus.NEW)
                .nextReviewDate(Instant.now())   // due immediately
                .build();
    }
}
