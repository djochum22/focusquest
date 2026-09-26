package com.example.focusquest.export;

import com.example.focusquest.vision.CameraObservation;
import com.example.focusquest.vision.OffTaskSignal;

import java.time.Instant;

/** One observation from the camera: a stretch of one off-task signal. */
public record CameraObservationBackup(
        Long id,
        Long sessionId,
        String clientEventId,
        OffTaskSignal signal,
        double confidence,
        Instant startedAt,
        Instant observedUntil,
        Instant receivedAt
) {

    static CameraObservationBackup from(CameraObservation observation) {
        return new CameraObservationBackup(observation.getId(), observation.getSession().getId(),
                observation.getClientEventId(), observation.getSignal(), observation.getConfidence(),
                observation.getStartedAt(), observation.getObservedUntil(), observation.getReceivedAt());
    }
}
