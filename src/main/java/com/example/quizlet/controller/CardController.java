package com.example.quizlet.controller;

import com.example.quizlet.dto.card.CardRequest;
import com.example.quizlet.dto.card.CardResponse;
import com.example.quizlet.entity.User;
import com.example.quizlet.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Card endpoints, nested under their deck.
 *
 * Public:  GET /api/decks/{deckId}/cards       (deck must be public or owned)
 * Owner:   POST /api/decks/{deckId}/cards,
 *          PUT /api/decks/{deckId}/cards/{cardId},
 *          DELETE /api/decks/{deckId}/cards/{cardId}
 */
@RestController
@RequestMapping("/api/decks/{deckId}/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @GetMapping
    public ResponseEntity<List<CardResponse>> list(@PathVariable Long deckId,
                                                   @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(cardService.listByDeck(deckId, currentUser));
    }

    @PostMapping
    public ResponseEntity<CardResponse> create(@PathVariable Long deckId,
                                               @Valid @RequestBody CardRequest request,
                                               @AuthenticationPrincipal User currentUser) {
        CardResponse created = cardService.create(deckId, request, currentUser);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{cardId}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(created);
    }

    @PutMapping("/{cardId}")
    public ResponseEntity<CardResponse> update(@PathVariable Long deckId,
                                               @PathVariable Long cardId,
                                               @Valid @RequestBody CardRequest request,
                                               @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(cardService.update(deckId, cardId, request, currentUser));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> delete(@PathVariable Long deckId,
                                       @PathVariable Long cardId,
                                       @AuthenticationPrincipal User currentUser) {
        cardService.delete(deckId, cardId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
