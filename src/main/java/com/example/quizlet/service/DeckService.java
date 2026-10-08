package com.example.quizlet.service;

import com.example.quizlet.dto.deck.DeckRequest;
import com.example.quizlet.dto.deck.DeckResponse;
import com.example.quizlet.entity.Deck;
import com.example.quizlet.entity.User;
import com.example.quizlet.exception.ResourceNotFoundException;
import com.example.quizlet.repository.DeckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for decks: ownership-guarded CRUD plus public search.
 */
@Service
@RequiredArgsConstructor
public class DeckService {

    private final DeckRepository deckRepository;

    /**
     * Creates a deck owned by the currently authenticated user.
     */
    @Transactional
    public DeckResponse create(DeckRequest request, User currentUser) {
        Deck deck = Deck.builder()
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .isPublic(request.isPublic())
                .creator(currentUser)
                .build();
        return DeckResponse.from(deckRepository.save(deck));
    }

    /**
     * Returns a single deck if it is public, or if the caller is its owner.
     */
    @Transactional(readOnly = true)
    public DeckResponse getById(Long id, User currentUser) {
        return DeckResponse.from(getViewable(id, currentUser));
    }

    /**
     * Search over PUBLIC decks by free-text query and/or exact category.
     * Blank values mean "no filter" for that dimension.
     */
    @Transactional(readOnly = true)
    public Page<DeckResponse> searchPublic(String query, String category, Pageable pageable) {
        String q = normalize(query);
        String cat = normalize(category);
        return deckRepository.searchPublicDecks(q, cat, pageable).map(DeckResponse::from);
    }

    /**
     * All decks owned by the given user (public and private).
     */
    @Transactional(readOnly = true)
    public Page<DeckResponse> getMyDecks(User currentUser, Pageable pageable) {
        return deckRepository.findByCreatorIdOrderByIdDesc(currentUser.getId(), pageable)
                .map(DeckResponse::from);
    }

    /**
     * Updates a deck. Only the owner may update it.
     */
    @Transactional
    public DeckResponse update(Long id, DeckRequest request, User currentUser) {
        Deck deck = getOwned(id, currentUser);
        deck.setTitle(request.title());
        deck.setDescription(request.description());
        deck.setCategory(request.category());
        deck.setPublic(request.isPublic());
        return DeckResponse.from(deck);
    }

    /**
     * Deletes a deck (and, via cascade, all of its cards). Owner only.
     */
    @Transactional
    public void delete(Long id, User currentUser) {
        Deck deck = getOwned(id, currentUser);
        deckRepository.delete(deck);
    }

    /**
     * Fetches a deck the caller is allowed to see (public or owned).
     */
    @Transactional(readOnly = true)
    public Deck getViewable(Long id, User currentUser) {
        Deck deck = deckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deck", id));

        boolean isOwner = currentUser != null && deck.getCreator().getId().equals(currentUser.getId());
        if (!deck.isPublic() && !isOwner) {
            // Hide the existence of private decks from non-owners.
            throw new ResourceNotFoundException("Deck", id);
        }
        return deck;
    }

    /**
     * Fetches a deck the caller owns; otherwise throws 403.
     */
    @Transactional(readOnly = true)
    public Deck getOwned(Long id, User currentUser) {
        Deck deck = deckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deck", id));

        if (!deck.getCreator().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the deck owner can modify this deck");
        }
        return deck;
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
