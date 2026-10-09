package com.example.quizlet.srs;

import com.example.quizlet.entity.ReviewQuality;
import com.example.quizlet.entity.UserCardProgress;

import java.time.Instant;

/**
 * Strategy for SRS scheduling: given the current review state and the
 * user's quality rating, computes the next schedule.
 *
 * Implementations must be pure (no I/O, no clock reads — "now" is passed in)
 * so algorithms are unit-testable and hot-swappable (e.g. SM-2 today,
 * a Mochi-style variant tomorrow) without touching service or persistence code.
 */
public interface ReviewSchedulingPolicy {

    /** Computes the next schedule for a card after one rated review. */
    SrsSchedule scheduleNext(UserCardProgress progress, ReviewQuality quality, Instant now);
}
