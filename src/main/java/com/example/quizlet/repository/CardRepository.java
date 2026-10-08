package com.example.quizlet.repository;

import com.example.quizlet.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByIdAndDeckId(Long id, Long deckId);
}
