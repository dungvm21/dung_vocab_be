package com.example.quizlet.service;

import com.example.quizlet.dto.deck.DeckRequest;
import com.example.quizlet.dto.deck.DeckResponse;
import com.example.quizlet.entity.Deck;
import com.example.quizlet.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Business contract for decks: ownership-guarded CRUD plus public search.
 */
public interface DeckService {

    /** Creates a deck owned by the currently authenticated user. */
    DeckResponse create(DeckRequest request, User currentUser);

    /** Returns a single deck if it is public, or if the caller is its owner. */
    DeckResponse getById(Long id, User currentUser);

    /** Search over PUBLIC decks by free-text query and/or exact category. */
    Page<DeckResponse> searchPublic(String query, String category, Pageable pageable);

    /** All decks owned by the given user (public and private). */
    Page<DeckResponse> getMyDecks(User currentUser, Pageable pageable);

    /** Updates a deck. Only the owner may update it. */
    DeckResponse update(Long id, DeckRequest request, User currentUser);

    /** Deletes a deck (and, via cascade, all of its cards). Owner only. */
    void delete(Long id, User currentUser);

    /** Fetches a deck the caller is allowed to see (public or owned). */
    Deck getViewable(Long id, User currentUser);

    /** Fetches a deck the caller owns; otherwise throws 403. */
    Deck getOwned(Long id, User currentUser);
}
