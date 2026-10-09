package com.example.quizlet.service;

import com.example.quizlet.dto.review.DueReviewsResponse;
import com.example.quizlet.dto.review.EnrollmentResponse;
import com.example.quizlet.dto.review.ReviewRequest;
import com.example.quizlet.dto.review.ReviewResponse;
import com.example.quizlet.dto.review.SavedReviewResponse;
import com.example.quizlet.entity.User;

/**
 * SRS review contract: due-review queue, quality-scored reviews (SM-2),
 * and review-list enrollment (single card or whole deck).
 */
public interface ReviewService {

    /** Cards due for review right now for the caller, oldest due first. */
    DueReviewsResponse getDueReviews(User user, int limit);

    /** Records one review and reschedules the card. First review creates the row. */
    ReviewResponse submitReview(Long cardId, ReviewRequest request, User user);

    /** Adds one card to the caller's review queue, due immediately. Idempotent. */
    SavedReviewResponse saveToReviewList(Long cardId, User user);

    /** Enrolls every card of a deck into the review queue. Idempotent per card. */
    EnrollmentResponse enrollDeck(Long deckId, User user);

    /** Removes a card from the caller's review queue. No-op if absent. */
    void removeFromReviewList(Long cardId, User user);
}
