package com.example.quizlet.controller;

import com.example.quizlet.dto.deck.DeckRequest;
import com.example.quizlet.dto.deck.DeckResponse;
import com.example.quizlet.entity.User;
import com.example.quizlet.service.DeckService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Deck endpoints.
 *
 * Public (no auth):  GET /api/decks/search, GET /api/decks/{id}
 * Authenticated:     POST /api/decks, GET /api/decks/me,
 *                    PUT/DELETE /api/decks/{id}
 */
@RestController
@RequestMapping("/api/decks")
@RequiredArgsConstructor
public class DeckController {

    private final DeckService deckService;

    /** Search public decks. Example: /api/decks/search?q=english&category=IELTS */
    @GetMapping("/search")
    public ResponseEntity<Page<DeckResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(deckService.searchPublic(q, category, pageable));
    }

    /** Decks owned by the authenticated user (public and private). */
    @GetMapping("/me")
    public ResponseEntity<Page<DeckResponse>> myDecks(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(deckService.getMyDecks(currentUser, pageable));
    }

    /** Single deck — readable if public, or by its owner. */
    @GetMapping("/{id}")
    public ResponseEntity<DeckResponse> getById(@PathVariable Long id,
                                                @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(deckService.getById(id, currentUser));
    }

    /** Creates a deck owned by the authenticated user. */
    @PostMapping
    public ResponseEntity<DeckResponse> create(@Valid @RequestBody DeckRequest request,
                                               @AuthenticationPrincipal User currentUser) {
        DeckResponse created = deckService.create(request, currentUser);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(created);
    }

    /** Full update. Owner only. */
    @PutMapping("/{id}")
    public ResponseEntity<DeckResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody DeckRequest request,
                                               @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(deckService.update(id, request, currentUser));
    }

    /** Delete. Owner only. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal User currentUser) {
        deckService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
