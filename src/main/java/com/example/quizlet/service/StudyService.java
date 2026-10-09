package com.example.quizlet.service;

import com.example.quizlet.dto.study.CardProgressResponse;
import com.example.quizlet.dto.study.DeckProgressResponse;
import com.example.quizlet.dto.study.QuizResponse;
import com.example.quizlet.dto.study.StudyAnswerRequest;
import com.example.quizlet.dto.study.StudyAnswerResponse;
import com.example.quizlet.entity.Card;
import com.example.quizlet.entity.CardProgress;
import com.example.quizlet.entity.Deck;
import com.example.quizlet.entity.StudyAttempt;
import com.example.quizlet.entity.StudyMode;
import com.example.quizlet.entity.User;
import com.example.quizlet.exception.ResourceNotFoundException;
import com.example.quizlet.learning.WordState;
import com.example.quizlet.repository.CardProgressRepository;
import com.example.quizlet.repository.CardRepository;
import com.example.quizlet.repository.StudyAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Study engine: generates multiple-choice quizzes, grades answers
 * (server-side), applies the per-user state machine (NOT_LEARNED →
 * STILL_LEARNING → MASTERED, wrong = streak reset), and reports progress.
 */
@Service
@RequiredArgsConstructor
public class StudyService {

    /** Correct option + distractors. */
    private static final int QUIZ_OPTION_COUNT = 4;
    private static final int MAX_QUIZ_QUESTIONS = 50;

    private final DeckService deckService;
    private final CardRepository cardRepository;
    private final CardProgressRepository cardProgressRepository;
    private final StudyAttemptRepository studyAttemptRepository;
    private final Random random = new Random();

    // ---- Quiz generation ----

    /**
     * Builds a multiple-choice quiz from a deck the caller can view.
     * Optional filters narrow the question pool by learning state and/or starred flag.
     */
    @Transactional(readOnly = true)
    public QuizResponse generateQuiz(Long deckId, User user, int count,
                                     WordState stateFilter, Boolean starredFilter) {
        Deck deck = deckService.getViewable(deckId, user);
        Map<Long, CardProgress> progressByCard = loadProgressMap(user, deck);

        List<Card> pool = deck.getCards().stream()
                .filter(card -> matchesFilters(card, progressByCard, stateFilter, starredFilter))
                .collect(Collectors.toCollection(ArrayList::new));
        if (pool.isEmpty()) {
            return new QuizResponse(deckId, "DEFINITION_TO_TERM", List.of());
        }

        Collections.shuffle(pool, random);
        int questionCount = Math.min(Math.max(count, 1), Math.min(MAX_QUIZ_QUESTIONS, pool.size()));

        List<QuizResponse.QuizQuestion> questions = new ArrayList<>(questionCount);
        for (int i = 0; i < questionCount; i++) {
            questions.add(buildQuestion(pool.get(i), deck.getCards()));
        }
        return new QuizResponse(deckId, "DEFINITION_TO_TERM", questions);
    }

    /**
     * Prompt = definition, options = terms. Distractors come from the same deck;
     * small decks simply get fewer options. The correct answer is never sent to the client.
     */
    private QuizResponse.QuizQuestion buildQuestion(Card target, List<Card> deckCards) {
        List<Card> distractors = deckCards.stream()
                .filter(c -> !c.getId().equals(target.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(distractors, random);

        List<QuizResponse.QuizOption> options = new ArrayList<>(QUIZ_OPTION_COUNT);
        options.add(new QuizResponse.QuizOption(target.getId(), target.getTerm()));
        distractors.stream()
                .limit(QUIZ_OPTION_COUNT - 1)
                .forEach(c -> options.add(new QuizResponse.QuizOption(c.getId(), c.getTerm())));
        Collections.shuffle(options, random);

        return new QuizResponse.QuizQuestion(target.getId(), target.getDefinition(), options);
    }

    // ---- Answer grading ----

    /**
     * Grades one answer, advances the state machine, persists progress + attempt history.
     */
    @Transactional
    public StudyAnswerResponse answer(Long deckId, StudyAnswerRequest request, User user) {
        Deck deck = deckService.getViewable(deckId, user);

        Card card = cardRepository.findByIdAndDeckId(request.cardId(), deckId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", request.cardId()));

        Card selected = request.selectedCardId() == null ? null
                : cardRepository.findByIdAndDeckId(request.selectedCardId(), deckId).orElse(null);
        boolean correct = selected != null && selected.getId().equals(card.getId());

        CardProgress progress = getOrCreateProgress(user, card);
        if (correct) {
            progress.recordCorrect();
        } else {
            progress.recordIncorrect();
        }
        // New entities are transient (not managed) — dirty checking won't flush them.
        cardProgressRepository.save(progress);

        studyAttemptRepository.save(StudyAttempt.builder()
                .user(user)
                .card(card)
                .deck(deck)
                .mode(parseMode(request.mode()))
                .correct(correct)
                .selectedCard(selected)
                .build());

        return new StudyAnswerResponse(
                correct,
                card.getId(),
                card.getId(),
                card.getTerm(),
                progress.getState(),
                progress.getConsecutiveCorrect(),
                deckProgress(deck, user));
    }

    private static StudyMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return StudyMode.MULTIPLE_CHOICE;
        }
        return StudyMode.valueOf(mode.trim().toUpperCase());
    }

    // ---- Progress & star ----

    @Transactional(readOnly = true)
    public DeckProgressResponse progress(Long deckId, User user) {
        Deck deck = deckService.getViewable(deckId, user);
        return deckProgress(deck, user);
    }

    /** Toggles the user's starred flag on a card (works on public decks too — it's a personal note). */
    @Transactional
    public CardProgressResponse toggleStar(Long deckId, Long cardId, User user) {
        deckService.getViewable(deckId, user);
        Card card = cardRepository.findByIdAndDeckId(cardId, deckId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", cardId));

        CardProgress progress = getOrCreateProgress(user, card);
        progress.toggleStar();
        cardProgressRepository.save(progress);
        return CardProgressResponse.from(progress);
    }

    // ---- Helpers ----

    private DeckProgressResponse deckProgress(Deck deck, User user) {
        Map<Long, CardProgress> progressByCard = loadProgressMap(user, deck);

        long total = deck.getCards().size();
        long mastered = 0;
        long stillLearning = 0;
        for (Card card : deck.getCards()) {
            WordState state = stateOf(card, progressByCard);
            if (state == WordState.MASTERED) {
                mastered++;
            } else if (state == WordState.STILL_LEARNING) {
                stillLearning++;
            }
        }
        long notLearned = total - mastered - stillLearning;
        double percent = total == 0 ? 0.0 : Math.round(mastered * 1000.0 / total) / 10.0;
        return new DeckProgressResponse(total, mastered, stillLearning, notLearned, percent);
    }

    /** Finds or creates (unsaved) the progress row for a user/card pair. */
    private CardProgress getOrCreateProgress(User user, Card card) {
        return cardProgressRepository.findByUserIdAndCardId(user.getId(), card.getId())
                .orElseGet(() -> CardProgress.builder()
                        .user(user)
                        .card(card)
                        .build());
    }

    private Map<Long, CardProgress> loadProgressMap(User user, Deck deck) {
        List<Long> cardIds = deck.getCards().stream().map(Card::getId).toList();
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        return cardProgressRepository.findByUserIdAndCardIdIn(user.getId(), cardIds).stream()
                .collect(Collectors.toMap(p -> p.getCard().getId(), Function.identity()));
    }

    private static WordState stateOf(Card card, Map<Long, CardProgress> progressByCard) {
        CardProgress progress = progressByCard.get(card.getId());
        return progress != null ? progress.getState() : WordState.NOT_LEARNED;
    }

    private static boolean matchesFilters(Card card, Map<Long, CardProgress> progressByCard,
                                          WordState stateFilter, Boolean starredFilter) {
        CardProgress progress = progressByCard.get(card.getId());
        WordState state = stateOf(card, progressByCard);
        boolean starred = progress != null && progress.isStarred();
        return (stateFilter == null || state == stateFilter)
                && (starredFilter == null || starred == starredFilter);
    }
}
