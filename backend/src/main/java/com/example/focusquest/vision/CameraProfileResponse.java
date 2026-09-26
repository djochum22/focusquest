package com.example.focusquest.vision;

import com.example.focusquest.session.TaskCategory;

import java.util.List;
import java.util.Map;

/**
 * API representation of a camera profile. {@code checks} lists the signals checked for the category,
 * in {@link OffTaskSignal} order, each with how many seconds it must last before a warning.
 */
public record CameraProfileResponse(
        TaskCategory category,
        WorkArea workArea,
        List<Check> checks,
        long graceSeconds,
        double minConfidence
) {

    public record Check(OffTaskSignal signal, long warningAfterSeconds) {
    }

    public static CameraProfileResponse from(CameraProfile profile) {
        List<Check> checks = profile.warningAfter().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new Check(entry.getKey(), entry.getValue().toSeconds()))
                .toList();
        return new CameraProfileResponse(profile.category(), profile.workArea(), checks,
                profile.grace().toSeconds(), profile.minConfidence());
    }
}
