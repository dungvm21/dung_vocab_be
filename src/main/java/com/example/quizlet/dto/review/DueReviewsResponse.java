package com.example.quizlet.dto.review;

import com.example.quizlet.entity.ReviewStatus;
import com.example.quizlet.entity.UserCardProgress;

import java.time.Instant;
import java.util.List;

/**
 * Cards due for review right now, oldest due first.
 */
public record DueReviewsResponse(
        long dueCount,
        Instant serverTime,
        List<ReviewItemResponse> items
) {

    public record ReviewItemResponse(
            Long cardId,
            String term,
            String definition,
            String hint,
            Long deckId,
            ReviewStatus status,
            int intervalDays,
            double easeFactor,
            int repetition,
            Instant nextReviewDate
    ) {
        public static ReviewItemResponse from(UserCardProgress progress) {
            return new ReviewItemResponse(
                    progress.getCard().getId(),
                    progress.getCard().getTerm(),
                    progress.getCard().getDefinition(),
                    progress.getCard().getHint(),
                    progress.getCard().getDeck().getId(),
                    progress.getStatus(),
                    progress.getIntervalDays(),
                    progress.getEaseFactor(),
                    progress.getRepetition(),
                    progress.getNextReviewDate()
            );
        }
    }

    public static DueReviewsResponse of(List<UserCardProgress> progressList, Instant now) {
        List<ReviewItemResponse> items = progressList.stream().map(ReviewItemResponse::from).toList();
        return new DueReviewsResponse(items.size(), now, items);
    }
}
