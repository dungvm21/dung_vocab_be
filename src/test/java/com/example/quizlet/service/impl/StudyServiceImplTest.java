package com.example.quizlet.service.impl;

import com.example.quizlet.dto.study.QuizResponse;
import com.example.quizlet.dto.study.StudyAnswerRequest;
import com.example.quizlet.dto.study.StudyAnswerResponse;
import com.example.quizlet.dto.study.CardProgressResponse;
import com.example.quizlet.entity.Card;
import com.example.quizlet.entity.CardProgress;
import com.example.quizlet.entity.Deck;
import com.example.quizlet.entity.StudyAttempt;
import com.example.quizlet.entity.User;
import com.example.quizlet.exception.ResourceNotFoundException;
import com.example.quizlet.learning.WordState;
import com.example.quizlet.repository.CardProgressRepository;
import com.example.quizlet.repository.CardRepository;
import com.example.quizlet.repository.StudyAttemptRepository;
import com.example.quizlet.service.DeckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StudyServiceImpl} (MCQ study engine).
 * Collaboration boundaries (DeckService, repositories) are mocked;
 * real entities are built via Lombok builders.
 */
@ExtendWith(MockitoExtension.class)
class StudyServiceImplTest {

    private static final long USER_ID = 9L;
    private static final long DECK_ID = 2L;

    @Mock
    private DeckService deckService;
    @Mock
    private CardRepository cardRepository;
    @Mock
    private CardProgressRepository cardProgressRepository;
    @Mock
    private StudyAttemptRepository studyAttemptRepository;

    @InjectMocks
    private StudyServiceImpl studyService;

    private final User user = User.builder().id(USER_ID).username("tester").build();

    // ---- fixtures ----

    private Card card(long id, String term, String definition) {
        return Card.builder().id(id).term(term).definition(definition).build();
    }

    private Deck deckWithCards(Card... cards) {
        return Deck.builder().id(DECK_ID).title("Deck").isPublic(true).cards(List.of(cards)).build();
    }

    private CardProgress progressRow(Card card, WordState state, int streak, boolean starred) {
        return CardProgress.builder()
                .user(user)
                .card(card)
                .state(state)
                .consecutiveCorrect(streak)
                .starred(starred)
                .build();
    }

    // ---- quiz generation ----

    @Test
    void generateQuiz_buildsQuestionsWithOptionsFromSameDeck() {
        Deck deck = deckWithCards(
                card(1, "ambition", "hoài bão"),
                card(2, "alleviate", "làm dịu"),
                card(3, "permission", "cho phép"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardProgressRepository.findByUserIdAndCardIdIn(USER_ID, List.of(1L, 2L, 3L)))
                .thenReturn(List.of());

        QuizResponse quiz = studyService.generateQuiz(DECK_ID, user, 10, null, null);

        assertThat(quiz.questions()).hasSize(3);
        assertThat(quiz.direction()).isEqualTo("DEFINITION_TO_TERM");
        for (QuizResponse.QuizQuestion question : quiz.questions()) {
            assertThat(question.options()).hasSize(3);
            assertThat(question.options())
                    .extracting(QuizResponse.QuizOption::cardId)
                    .containsExactlyInAnyOrder(1L, 2L, 3L);
            // the question's own card must be among the options (as the correct one)
            assertThat(question.options())
                    .extracting(QuizResponse.QuizOption::cardId)
                    .contains(question.cardId());
        }
    }

    @Test
    void generateQuiz_limitsQuestionsToRequestedCount() {
        Deck deck = deckWithCards(
                card(1, "a", "da"), card(2, "b", "db"), card(3, "c", "dc"),
                card(4, "d", "dd"), card(5, "e", "de"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardProgressRepository.findByUserIdAndCardIdIn(eq(USER_ID), any())).thenReturn(List.of());

        QuizResponse quiz = studyService.generateQuiz(DECK_ID, user, 2, null, null);

        assertThat(quiz.questions()).hasSize(2);
        assertThat(quiz.questions().get(0).options()).hasSize(4); // correct + 3 distractors
    }

    @Test
    void generateQuiz_filteredPoolEmpty_returnsNoQuestions() {
        Deck deck = deckWithCards(card(1, "a", "da"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardProgressRepository.findByUserIdAndCardIdIn(USER_ID, List.of(1L)))
                .thenReturn(List.of());

        QuizResponse quiz = studyService.generateQuiz(DECK_ID, user, 10, WordState.MASTERED, null);

        assertThat(quiz.questions()).isEmpty();
    }

    @Test
    void generateQuiz_appliesStateFilter() {
        Card mastered = card(1, "a", "da");
        Deck deck = deckWithCards(mastered, card(2, "b", "db"), card(3, "c", "dc"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardProgressRepository.findByUserIdAndCardIdIn(eq(USER_ID), any()))
                .thenReturn(List.of(progressRow(mastered, WordState.MASTERED, 2, false)));

        QuizResponse quiz = studyService.generateQuiz(DECK_ID, user, 10, WordState.NOT_LEARNED, null);

        assertThat(quiz.questions()).hasSize(2);
        assertThat(quiz.questions())
                .extracting(QuizResponse.QuizQuestion::cardId)
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void generateQuiz_appliesStarredFilter() {
        Card starred = card(1, "a", "da");
        Deck deck = deckWithCards(starred, card(2, "b", "db"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardProgressRepository.findByUserIdAndCardIdIn(eq(USER_ID), any()))
                .thenReturn(List.of(progressRow(starred, WordState.STILL_LEARNING, 1, true)));

        QuizResponse quiz = studyService.generateQuiz(DECK_ID, user, 10, null, true);

        assertThat(quiz.questions()).hasSize(1);
        assertThat(quiz.questions().get(0).cardId()).isEqualTo(1L);
    }

    @Test
    void generateQuiz_deckWithoutCards_returnsEmptyQuestions() {
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deckWithCards());

        QuizResponse quiz = studyService.generateQuiz(DECK_ID, user, 10, null, null);

        assertThat(quiz.questions()).isEmpty();
    }

    // ---- answer grading ----

    @Test
    void answer_firstCorrectAnswer_createsSavesProgressAndLogsAttempt() {
        Deck deck = deckWithCards(card(5, "ambition", "hoài bão"));
        Card target = deck.getCards().get(0);
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardRepository.findByIdAndDeckId(5L, DECK_ID)).thenReturn(Optional.of(target));
        when(cardProgressRepository.findByUserIdAndCardId(USER_ID, 5L))
                .thenReturn(Optional.empty());

        StudyAnswerResponse response = studyService.answer(
                DECK_ID, new StudyAnswerRequest(5L, 5L, null), user);

        assertThat(response.correct()).isTrue();
        assertThat(response.state()).isEqualTo(WordState.STILL_LEARNING);
        assertThat(response.consecutiveCorrect()).isEqualTo(1);

        // regression guard: new progress rows MUST be explicitly saved
        // (transient entities are not dirty-checked)
        ArgumentCaptor<CardProgress> progressCaptor = ArgumentCaptor.forClass(CardProgress.class);
        verify(cardProgressRepository).save(progressCaptor.capture());
        assertThat(progressCaptor.getValue().getState()).isEqualTo(WordState.STILL_LEARNING);
        assertThat(progressCaptor.getValue().getConsecutiveCorrect()).isEqualTo(1);

        ArgumentCaptor<StudyAttempt> attemptCaptor = ArgumentCaptor.forClass(StudyAttempt.class);
        verify(studyAttemptRepository).save(attemptCaptor.capture());
        StudyAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.isCorrect()).isTrue();
        assertThat(attempt.getCard()).isEqualTo(target);
        assertThat(attempt.getSelectedCard()).isEqualTo(target);
        assertThat(attempt.getMode()).isEqualTo(com.example.quizlet.entity.StudyMode.MULTIPLE_CHOICE);
    }

    @Test
    void answer_wrongAnswer_demotesMasteredCardAndResetsStreak() {
        Deck deck = deckWithCards(card(5, "a", "da"));
        Card target = deck.getCards().get(0);
        Card distractor = card(6, "b", "db");
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardRepository.findByIdAndDeckId(5L, DECK_ID)).thenReturn(Optional.of(target));
        when(cardRepository.findByIdAndDeckId(6L, DECK_ID)).thenReturn(Optional.of(distractor));
        when(cardProgressRepository.findByUserIdAndCardId(USER_ID, 5L))
                .thenReturn(Optional.of(progressRow(target, WordState.MASTERED, 2, false)));

        StudyAnswerResponse response = studyService.answer(
                DECK_ID, new StudyAnswerRequest(5L, 6L, null), user);

        assertThat(response.correct()).isFalse();
        assertThat(response.state()).isEqualTo(WordState.STILL_LEARNING);
        assertThat(response.consecutiveCorrect()).isZero();

        ArgumentCaptor<CardProgress> captor = ArgumentCaptor.forClass(CardProgress.class);
        verify(cardProgressRepository).save(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo(WordState.STILL_LEARNING);
        assertThat(captor.getValue().getConsecutiveCorrect()).isZero();
    }

    @Test
    void answer_selectedCardOutsideDeck_countsAsIncorrect() {
        Deck deck = deckWithCards(card(5, "a", "da"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardRepository.findByIdAndDeckId(5L, DECK_ID))
                .thenReturn(Optional.of(deck.getCards().get(0)));
        when(cardRepository.findByIdAndDeckId(999L, DECK_ID)).thenReturn(Optional.empty());
        when(cardProgressRepository.findByUserIdAndCardId(USER_ID, 5L))
                .thenReturn(Optional.empty());

        StudyAnswerResponse response = studyService.answer(
                DECK_ID, new StudyAnswerRequest(5L, 999L, null), user);

        assertThat(response.correct()).isFalse();

        ArgumentCaptor<StudyAttempt> captor = ArgumentCaptor.forClass(StudyAttempt.class);
        verify(studyAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getSelectedCard()).isNull();
    }

    @Test
    void answer_cardNotInDeck_throwsNotFound() {
        Deck deck = deckWithCards(card(5, "a", "da"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardRepository.findByIdAndDeckId(42L, DECK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studyService.answer(
                DECK_ID, new StudyAnswerRequest(42L, 42L, null), user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- progress ----

    @Test
    void progress_countsStatesAndComputesPercent() {
        Deck deck = deckWithCards(
                card(1, "a", "da"), card(2, "b", "db"),
                card(3, "c", "dc"), card(4, "d", "dd"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardProgressRepository.findByUserIdAndCardIdIn(eq(USER_ID), any())).thenReturn(List.of(
                progressRow(card(1, "a", "da"), WordState.MASTERED, 2, false),
                progressRow(card(2, "b", "db"), WordState.MASTERED, 2, false),
                progressRow(card(3, "c", "dc"), WordState.STILL_LEARNING, 1, false)));

        var result = studyService.progress(DECK_ID, user);

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.mastered()).isEqualTo(2);
        assertThat(result.stillLearning()).isEqualTo(1);
        assertThat(result.notLearned()).isEqualTo(1);
        assertThat(result.percent()).isEqualTo(50.0);
    }

    @Test
    void progress_emptyDeck_returnsZeros() {
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deckWithCards());

        var result = studyService.progress(DECK_ID, user);

        assertThat(result.total()).isZero();
        assertThat(result.mastered()).isZero();
        assertThat(result.notLearned()).isZero();
        assertThat(result.percent()).isZero();
    }

    // ---- star ----

    @Test
    void toggleStar_cardWithoutProgress_createsStarredRow() {
        Deck deck = deckWithCards(card(5, "a", "da"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardRepository.findByIdAndDeckId(5L, DECK_ID))
                .thenReturn(Optional.of(deck.getCards().get(0)));
        when(cardProgressRepository.findByUserIdAndCardId(USER_ID, 5L))
                .thenReturn(Optional.empty());

        CardProgressResponse response = studyService.toggleStar(DECK_ID, 5L, user);

        assertThat(response.starred()).isTrue();
        assertThat(response.state()).isEqualTo(WordState.NOT_LEARNED.name());

        ArgumentCaptor<CardProgress> captor = ArgumentCaptor.forClass(CardProgress.class);
        verify(cardProgressRepository).save(captor.capture());
        assertThat(captor.getValue().isStarred()).isTrue();
    }

    @Test
    void toggleStar_togglesExistingRowOff() {
        Deck deck = deckWithCards(card(5, "a", "da"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardRepository.findByIdAndDeckId(5L, DECK_ID))
                .thenReturn(Optional.of(deck.getCards().get(0)));
        when(cardProgressRepository.findByUserIdAndCardId(USER_ID, 5L))
                .thenReturn(Optional.of(progressRow(deck.getCards().get(0), WordState.NOT_LEARNED, 0, true)));

        CardProgressResponse response = studyService.toggleStar(DECK_ID, 5L, user);

        assertThat(response.starred()).isFalse();
    }

    @Test
    void toggleStar_cardNotInDeck_throwsNotFound() {
        Deck deck = deckWithCards(card(5, "a", "da"));
        when(deckService.getViewable(DECK_ID, user)).thenReturn(deck);
        when(cardRepository.findByIdAndDeckId(42L, DECK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studyService.toggleStar(DECK_ID, 42L, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
