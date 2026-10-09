package com.example.quizlet.service;

import com.example.quizlet.dto.card.CardRequest;
import com.example.quizlet.dto.card.CardResponse;
import com.example.quizlet.entity.User;

import java.util.List;

/**
 * Business contract for cards inside a deck. Writes are owner-only;
 * reads are allowed for any deck the caller can view.
 */
public interface CardService {

    /** Lists all cards of a deck (public decks readable by everyone). */
    List<CardResponse> listByDeck(Long deckId, User currentUser);

    /** Adds a new card to a deck. Owner only. */
    CardResponse create(Long deckId, CardRequest request, User currentUser);

    /** Updates an existing card. Owner of the containing deck only. */
    CardResponse update(Long deckId, Long cardId, CardRequest request, User currentUser);

    /** Deletes an existing card. Owner of the containing deck only. */
    void delete(Long deckId, Long cardId, User currentUser);
}
