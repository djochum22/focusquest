package com.example.focusquest.vision;

import com.example.focusquest.session.TaskCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The camera profile of every task category. Which work area a category has is a product decision
 * and fixed here; the timings and the minimum confidence are configuration
 * ({@code focusquest.camera.*}), the same for every category, to be tuned once real detection
 * exists. Users cannot change either yet.
 */
@Component
public class CameraProfiles {

    private static final Map<TaskCategory, WorkArea> WORK_AREAS = new EnumMap<>(Map.of(
            TaskCategory.CODING, WorkArea.SCREEN,
            TaskCategory.WORK, WorkArea.SCREEN,
            TaskCategory.ADMINISTRATION, WorkArea.SCREEN,
            TaskCategory.STUDYING, WorkArea.SCREEN_OR_DESK,
            TaskCategory.READING, WorkArea.SCREEN_OR_DESK,
            TaskCategory.WRITING, WorkArea.SCREEN_OR_DESK,
            TaskCategory.PLANNING, WorkArea.SCREEN_OR_DESK,
            TaskCategory.CREATIVE_WORK, WorkArea.ANYWHERE,
            TaskCategory.OTHER, WorkArea.ANYWHERE,
            TaskCategory.TASK_FREE, WorkArea.ANYWHERE));

    private final Map<TaskCategory, CameraProfile> profiles = new EnumMap<>(TaskCategory.class);

    public CameraProfiles(@Value("${focusquest.camera.warning-after.away}") Duration awayWarning,
                          @Value("${focusquest.camera.warning-after.phone}") Duration phoneWarning,
                          @Value("${focusquest.camera.warning-after.looking-away}") Duration lookingAwayWarning,
                          @Value("${focusquest.camera.grace}") Duration grace,
                          @Value("${focusquest.camera.min-confidence}") double minConfidence) {
        for (Duration duration : List.of(awayWarning, phoneWarning, lookingAwayWarning)) {
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("focusquest.camera.warning-after.* must be positive");
            }
        }
        if (grace.isNegative()) {
            throw new IllegalArgumentException("focusquest.camera.grace must not be negative");
        }
        if (minConfidence <= 0 || minConfidence > 1) {
            throw new IllegalArgumentException("focusquest.camera.min-confidence must be in (0, 1]");
        }
        for (TaskCategory category : TaskCategory.values()) {
            WorkArea workArea = WORK_AREAS.get(category);
            if (workArea == null) {
                throw new IllegalStateException("No camera work area for task category " + category);
            }
            Map<OffTaskSignal, Duration> warningAfter = new EnumMap<>(OffTaskSignal.class);
            warningAfter.put(OffTaskSignal.AWAY, awayWarning);
            warningAfter.put(OffTaskSignal.PHONE, phoneWarning);
            if (workArea != WorkArea.ANYWHERE) {
                warningAfter.put(OffTaskSignal.LOOKING_AWAY, lookingAwayWarning);
            }
            profiles.put(category, new CameraProfile(category, workArea, Map.copyOf(warningAfter), grace,
                    minConfidence));
        }
    }

    public CameraProfile forCategory(TaskCategory category) {
        return profiles.get(category);
    }

    /** Every profile, in the order the categories are declared. */
    public List<CameraProfile> all() {
        return Arrays.stream(TaskCategory.values()).map(profiles::get).toList();
    }
}
