package com.example.quizlet.service;

import com.example.quizlet.dto.study.CardProgressResponse;
import com.example.quizlet.dto.study.DeckProgressResponse;
import com.example.quizlet.dto.study.QuizResponse;
import com.example.quizlet.dto.study.StudyAnswerRequest;
import com.example.quizlet.dto.study.StudyAnswerResponse;
import com.example.quizlet.entity.User;
import com.example.quizlet.learning.WordState;

/**
 * MCQ study contract: quiz generation, server-side grading,
 * per-user state machine and progress reporting.
 */
public interface StudyService {

    /** Builds a multiple-choice quiz from a deck the caller can view. */
    QuizResponse generateQuiz(Long deckId, User user, int count,
                              WordState stateFilter, Boolean starredFilter);

    /** Grades one answer, advances the state machine, persists progress + attempt history. */
    StudyAnswerResponse answer(Long deckId, StudyAnswerRequest request, User user);

    /** Deck-wide learning progress for the caller. */
    DeckProgressResponse progress(Long deckId, User user);

    /** Toggles the user's starred flag on a card. */
    CardProgressResponse toggleStar(Long deckId, Long cardId, User user);
}
