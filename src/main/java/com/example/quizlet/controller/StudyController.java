package com.example.quizlet.controller;

import com.example.quizlet.dto.study.CardProgressResponse;
import com.example.quizlet.dto.study.DeckProgressResponse;
import com.example.quizlet.dto.study.QuizResponse;
import com.example.quizlet.dto.study.StudyAnswerRequest;
import com.example.quizlet.dto.study.StudyAnswerResponse;
import com.example.quizlet.entity.User;
import com.example.quizlet.learning.WordState;
import com.example.quizlet.service.StudyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Study endpoints (multiple-choice learning). All require authentication —
 * progress is tracked per user (see SecurityConfig: the /api/decks/&#42;/study/&#42;&#42;
 * rule is matched BEFORE the public GET /api/decks/&#42;&#42; rule).
 *
 * GET  /api/decks/{deckId}/study/quiz?count=&state=&starred=
 * POST /api/decks/{deckId}/study/answer
 * GET  /api/decks/{deckId}/study/progress
 * POST /api/decks/{deckId}/study/cards/{cardId}/star
 */
@RestController
@RequestMapping("/api/decks/{deckId}/study")
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    @GetMapping("/quiz")
    public QuizResponse quiz(@PathVariable Long deckId,
                             @RequestParam(defaultValue = "10") int count,
                             @RequestParam(required = false) String state,
                             @RequestParam(required = false) Boolean starred,
                             @AuthenticationPrincipal User currentUser) {
        WordState stateFilter = state == null ? null : WordState.valueOf(state.trim().toUpperCase());
        return studyService.generateQuiz(deckId, currentUser, count, stateFilter, starred);
    }

    @PostMapping("/answer")
    public StudyAnswerResponse answer(@PathVariable Long deckId,
                                      @Valid @RequestBody StudyAnswerRequest request,
                                      @AuthenticationPrincipal User currentUser) {
        return studyService.answer(deckId, request, currentUser);
    }

    @GetMapping("/progress")
    public DeckProgressResponse progress(@PathVariable Long deckId,
                                         @AuthenticationPrincipal User currentUser) {
        return studyService.progress(deckId, currentUser);
    }

    @PostMapping("/cards/{cardId}/star")
    public CardProgressResponse toggleStar(@PathVariable Long deckId,
                                           @PathVariable Long cardId,
                                           @AuthenticationPrincipal User currentUser) {
        return studyService.toggleStar(deckId, cardId, currentUser);
    }
}
