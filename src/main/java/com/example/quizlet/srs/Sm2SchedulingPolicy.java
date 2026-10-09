package com.example.quizlet.srs;

import com.example.quizlet.entity.ReviewQuality;
import com.example.quizlet.entity.ReviewStatus;
import com.example.quizlet.entity.UserCardProgress;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Modified SuperMemo SM-2 algorithm (Anki-style quality 1–4).
 *
 * AGAIN (1): lapse          → repetition 0, relearn in RELEARN_MINUTES, ease −0.20
 * HARD  (2): struggle       → interval × HARD_MULTIPLIER (or relearn on first sight), ease −0.15
 * GOOD  (3): correct        → repetition+1; first success → 1 day, then interval × easeFactor
 * EASY  (4): instant recall → like GOOD with EASY_BONUS on the interval, ease +0.10
 *
 * Ease is clamped to [MIN_EASE, MAX_EASE]; interval to [1, MAX_INTERVAL_DAYS];
 * interval ≥ MASTERED_INTERVAL_DAYS ⇒ MASTERED.
 */
@Component
public class Sm2SchedulingPolicy implements ReviewSchedulingPolicy {

    public static final double INITIAL_EASE = 2.5;
    public static final double MIN_EASE = 1.3;
    public static final double MAX_EASE = 2.8;
    public static final int MAX_INTERVAL_DAYS = 365;
    public static final int MASTERED_INTERVAL_DAYS = 21;
    /** Relearn delay after a lapse (AGAIN). */
    public static final int RELEARN_MINUTES = 10;
    /** HARD keeps some growth but much slower than the ease factor would give. */
    public static final double HARD_MULTIPLIER = 1.2;
    /** EASY stretches the interval further on top of the ease factor. */
    public static final double EASY_BONUS = 1.3;

    @Override
    public SrsSchedule scheduleNext(UserCardProgress progress, ReviewQuality quality, Instant now) {
        return switch (quality) {
            case AGAIN -> scheduleLapse(progress, now);
            case HARD -> scheduleHard(progress, now);
            case GOOD, EASY -> scheduleSuccess(progress, quality, now);
        };
    }

    private SrsSchedule scheduleLapse(UserCardProgress progress, Instant now) {
        double ease = clampEase(progress.getEaseFactor() - 0.20);
        return new SrsSchedule(0, 0, ease,
                now.plus(RELEARN_MINUTES, ChronoUnit.MINUTES), ReviewStatus.LEARNING);
    }

    private SrsSchedule scheduleHard(UserCardProgress progress, Instant now) {
        double ease = clampEase(progress.getEaseFactor() - 0.15);
        if (progress.getStatus() == ReviewStatus.NEW || progress.getRepetition() == 0) {
            // First sight not yet recalled cleanly — short relearn cycle.
            return new SrsSchedule(0, progress.getRepetition(), ease,
                    now.plus(RELEARN_MINUTES, ChronoUnit.MINUTES), ReviewStatus.LEARNING);
        }
        int interval = capInterval((int) Math.round(progress.getIntervalDays() * HARD_MULTIPLIER));
        return new SrsSchedule(interval, progress.getRepetition(), ease,
                now.plus(interval, ChronoUnit.DAYS), statusFor(interval));
    }

    private SrsSchedule scheduleSuccess(UserCardProgress progress, ReviewQuality quality, Instant now) {
        int repetition = progress.getRepetition() + 1;
        int interval;
        if (repetition == 1) {
            interval = quality == ReviewQuality.EASY ? 3 : 1;
        } else {
            double multiplier = progress.getEaseFactor()
                    * (quality == ReviewQuality.EASY ? EASY_BONUS : 1.0);
            interval = capInterval((int) Math.max(1, Math.round(progress.getIntervalDays() * multiplier)));
        }
        double ease = quality == ReviewQuality.EASY
                ? clampEase(progress.getEaseFactor() + 0.10)
                : progress.getEaseFactor();
        return new SrsSchedule(interval, repetition, ease,
                now.plus(interval, ChronoUnit.DAYS), statusFor(interval));
    }

    private static double clampEase(double ease) {
        return Math.max(MIN_EASE, Math.min(MAX_EASE, ease));
    }

    private static int capInterval(int days) {
        return Math.min(Math.max(days, 1), MAX_INTERVAL_DAYS);
    }

    private static ReviewStatus statusFor(int intervalDays) {
        return intervalDays >= MASTERED_INTERVAL_DAYS ? ReviewStatus.MASTERED : ReviewStatus.REVIEW;
    }
}
