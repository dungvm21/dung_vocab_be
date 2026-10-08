package com.example.quizlet.repository;

import com.example.quizlet.entity.Deck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeckRepository extends JpaRepository<Deck, Long> {

    Page<Deck> findByCreatorIdOrderByIdDesc(Long creatorId, Pageable pageable);

    Optional<Deck> findByIdAndIsPublicTrue(Long id);

    /**
     * Full-text-ish search over public decks. Empty/null parameters are
     * treated as "no filter", so the caller can pass either or both.
     */
    @Query("""
            select d from Deck d
            where d.isPublic = true
              and (coalesce(:q, '') = ''
                   or lower(d.title) like lower(concat('%', :q, '%'))
                   or lower(d.category) like lower(concat('%', :q, '%')))
              and (coalesce(:category, '') = ''
                   or lower(d.category) = lower(:category))
            order by d.id desc
            """)
    Page<Deck> searchPublicDecks(@Param("q") String q,
                                 @Param("category") String category,
                                 Pageable pageable);
}
