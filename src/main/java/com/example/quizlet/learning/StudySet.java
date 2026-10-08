package com.example.quizlet.learning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A study set (deck) of vocabulary cards with progress tracking,
 * filtering by state / starred flag, and shuffling.
 */
public class StudySet {

    private final String title;
    private final Map<Long, VocabularyCard> cards = new LinkedHashMap<>();
    private final Random random = new Random();

    public StudySet(String title) {
        this.title = title;
    }

    public void add(VocabularyCard card) {
        cards.put(card.getId(), card);
    }

    public VocabularyCard get(Long id) {
        return cards.get(id);
    }

    /** Progress = (mastered / total) * 100. Empty set = 0. */
    public double progressPercent() {
        if (cards.isEmpty()) {
            return 0.0;
        }
        long mastered = cards.values().stream()
                .filter(c -> c.getState() == WordState.MASTERED)
                .count();
        return (mastered * 100.0) / cards.size();
    }

    public List<VocabularyCard> filterByState(WordState state) {
        return cards.values().stream()
                .filter(c -> c.getState() == state)
                .toList();
    }

    public List<VocabularyCard> filterStarred() {
        return cards.values().stream()
                .filter(VocabularyCard::isStarred)
                .toList();
    }

    /** Randomized order of all cards — prevents memorizing by position. */
    public List<VocabularyCard> shuffle() {
        List<VocabularyCard> order = new ArrayList<>(cards.values());
        Collections.shuffle(order, random);
        return order;
    }

    public String getTitle() {
        return title;
    }

    public int size() {
        return cards.size();
    }
}
