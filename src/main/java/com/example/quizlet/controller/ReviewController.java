package com.example.quizlet.controller;

import com.example.quizlet.dto.review.DueReviewsResponse;
import com.example.quizlet.dto.review.EnrollmentResponse;
import com.example.quizlet.dto.review.ReviewRequest;
import com.example.quizlet.dto.review.ReviewResponse;
import com.example.quizlet.dto.review.SavedReviewResponse;
import com.example.quizlet.entity.User;
import com.example.quizlet.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * SRS review endpoints (all authenticated via the global anyRequest rule).
 *
 * GET    /api/reviews/due?limit=20          → cards due for review now
 * POST   /api/reviews/{cardId}              → submit quality score 1–4, get new schedule
 * POST   /api/reviews/{cardId}/save         → add one card to the review queue (due now)
 * POST   /api/reviews/decks/{deckId}/enroll → add all deck cards to the review queue
 * DELETE /api/reviews/{cardId}              → remove card from the review queue
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/due")
    public DueReviewsResponse due(@RequestParam(defaultValue = "20") int limit,
                                  @AuthenticationPrincipal User currentUser) {
        return reviewService.getDueReviews(currentUser, limit);
    }

    /** Enroll a whole deck. Declared before /{cardId} so "decks" is never read as a card id. */
    @PostMapping("/decks/{deckId}/enroll")
    public EnrollmentResponse enrollDeck(@PathVariable Long deckId,
                                         @AuthenticationPrincipal User currentUser) {
        return reviewService.enrollDeck(deckId, currentUser);
    }

    @PostMapping("/{cardId}")
    public ReviewResponse review(@PathVariable Long cardId,
                                 @Valid @RequestBody ReviewRequest request,
                                 @AuthenticationPrincipal User currentUser) {
        return reviewService.submitReview(cardId, request, currentUser);
    }

    @PostMapping("/{cardId}/save")
    public SavedReviewResponse save(@PathVariable Long cardId,
                                    @AuthenticationPrincipal User currentUser) {
        return reviewService.saveToReviewList(cardId, currentUser);
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> remove(@PathVariable Long cardId,
                                       @AuthenticationPrincipal User currentUser) {
        reviewService.removeFromReviewList(cardId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
