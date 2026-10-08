package com.example.quizlet.learning;

/**
 * Mock execution: shows one word ("hello") moving through the states
 * based on a sequence of user answers.
 */
public class LearningDemo {

    public static void main(String[] args) {
        StudySet set = new StudySet("English Basics");
        VocabularyCard hello = new VocabularyCard(1L, "hello", "xin chào");
        VocabularyCard world = new VocabularyCard(2L, "world", "thế giới");
        VocabularyCard apple = new VocabularyCard(3L, "apple", "quả táo");
        set.add(hello);
        set.add(world);
        set.add(apple);

        System.out.println("== Track one word: hello ==");
        print(set, hello);

        System.out.println("-- wrong on first try -> stays NOT_LEARNED");
        hello.recordIncorrect();
        print(set, hello);

        System.out.println("-- correct #1 -> STILL_LEARNING, streak 1");
        hello.recordCorrect();
        print(set, hello);

        System.out.println("-- correct #2 -> MASTERED, streak 2");
        hello.recordCorrect();
        print(set, hello);

        System.out.println("-- wrong -> demoted to STILL_LEARNING, streak reset");
        hello.recordIncorrect();
        print(set, hello);

        System.out.println("-- correct #1 -> STILL_LEARNING again, streak 1");
        hello.recordCorrect();
        print(set, hello);

        System.out.println("-- correct #2 -> MASTERED again");
        hello.recordCorrect();
        print(set, hello);

        System.out.println("\n== Star & filter ==");
        hello.toggleStar();
        world.toggleStar();
        System.out.println("Starred words : " + set.filterStarred().stream().map(VocabularyCard::getFront).toList());

        System.out.println("\n== Progress & shuffle ==");
        apple.recordCorrect();
        apple.recordCorrect();
        world.recordCorrect();
        System.out.println("Progress      : %.1f%%".formatted(set.progressPercent()));
        System.out.println("Mastered      : " + fronts(set.filterByState(WordState.MASTERED)));
        System.out.println("StillLearning : " + fronts(set.filterByState(WordState.STILL_LEARNING)));
        System.out.println("NotLearned    : " + fronts(set.filterByState(WordState.NOT_LEARNED)));
        System.out.println("Shuffle #1    : " + fronts(set.shuffle()));
        System.out.println("Shuffle #2    : " + fronts(set.shuffle()));
    }

    private static String fronts(java.util.List<VocabularyCard> cards) {
        return cards.stream().map(VocabularyCard::getFront).toList().toString();
    }

    private static void print(StudySet set, VocabularyCard card) {
        System.out.println("   %s -> state=%s, streak=%d, set progress=%.1f%%".formatted(
                card.getFront(), card.getState(), card.getConsecutiveCorrect(), set.progressPercent()));
    }
}
