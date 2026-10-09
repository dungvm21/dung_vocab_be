package com.example.quizlet.service.impl;

import com.example.quizlet.dto.card.CardRequest;
import com.example.quizlet.dto.card.CardResponse;
import com.example.quizlet.entity.Card;
import com.example.quizlet.entity.Deck;
import com.example.quizlet.entity.User;
import com.example.quizlet.exception.ResourceNotFoundException;
import com.example.quizlet.repository.CardRepository;
import com.example.quizlet.service.CardService;
import com.example.quizlet.service.DeckService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link CardService}: card CRUD guarded by deck ownership/visibility.
 */
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final CardRepository cardRepository;
    private final DeckService deckService;

    /**
     * Lists all cards of a deck (public decks readable by everyone).
     */
    @Transactional(readOnly = true)
    public List<CardResponse> listByDeck(Long deckId, User currentUser) {
        Deck deck = deckService.getViewable(deckId, currentUser);
        return deck.getCards().stream().map(CardResponse::from).toList();
    }

    /**
     * Adds a new card to a deck. Owner only.
     */
    @Transactional
    public CardResponse create(Long deckId, CardRequest request, User currentUser) {
        Deck deck = deckService.getOwned(deckId, currentUser);

        Card card = Card.builder()
                .term(request.term())
                .definition(request.definition())
                .hint(request.hint())
                .deck(deck)
                .build();
        return CardResponse.from(cardRepository.save(card));
    }

    /**
     * Updates an existing card. Owner of the containing deck only.
     */
    @Transactional
    public CardResponse update(Long deckId, Long cardId, CardRequest request, User currentUser) {
        deckService.getOwned(deckId, currentUser);
        Card card = findInDeck(cardId, deckId);

        card.setTerm(request.term());
        card.setDefinition(request.definition());
        card.setHint(request.hint());
        return CardResponse.from(card);
    }

    /**
     * Deletes an existing card. Owner of the containing deck only.
     */
    @Transactional
    public void delete(Long deckId, Long cardId, User currentUser) {
        deckService.getOwned(deckId, currentUser);
        Card card = findInDeck(cardId, deckId);
        cardRepository.delete(card);
    }

    /** Ensures the card exists AND belongs to the given deck (prevents cross-deck access). */
    private Card findInDeck(Long cardId, Long deckId) {
        return cardRepository.findByIdAndDeckId(cardId, deckId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", cardId));
    }
}
