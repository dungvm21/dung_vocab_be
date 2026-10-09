package com.example.quizlet.dto.study;

import java.util.List;

/**
 * A generated quiz: questions WITHOUT the correct answer — grading happens server-side.
 */
public record QuizResponse(
        Long deckId,
        String direction,          // DEFINITION_TO_TERM (reverse mode later)
        List<QuizQuestion> questions
) {

    public record QuizQuestion(
            Long cardId,           // the card being asked about
            String prompt,         // the definition to translate
            List<QuizOption> options
    ) {
    }

    public record QuizOption(
            Long cardId,           // client echoes this back as selectedCardId
            String text            // the term
    ) {
    }
}
