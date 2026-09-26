package com.example.focusquest.vision;

import com.example.focusquest.session.TaskCategory;

import java.time.Duration;
import java.util.Map;

/**
 * What the camera checks during a session of one task category (requirements specification,
 * section 21). {@code warningAfter} holds only the signals checked for this category, with how long
 * each must last before the user is warned. {@code grace} is how long after the warning subtraction
 * starts, and {@code minConfidence} the least confidence at which an observation counts.
 */
public record CameraProfile(
        TaskCategory category,
        WorkArea workArea,
        Map<OffTaskSignal, Duration> warningAfter,
        Duration grace,
        double minConfidence
) {
}
