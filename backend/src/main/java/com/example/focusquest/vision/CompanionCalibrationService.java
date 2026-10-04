package com.example.focusquest.vision;

import com.example.focusquest.session.SessionService;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Calibrating the companion program from Settings, so nobody needs a terminal for it. The web app asks;
 * the program's next answer says {@code calibrate}; the program guides the user in its camera window and
 * reports how it went. Only when and whether it worked reach the backend, never the angles.
 */
@Service
public class CompanionCalibrationService {

    private final CompanionCredentialRepository repository;
    private final CompanionCredentialService companionCredentialService;
    private final CameraSettingsService cameraSettingsService;
    private final SessionService sessionService;
    private final ClockProvider clockProvider;

    public CompanionCalibrationService(CompanionCredentialRepository repository,
                                       CompanionCredentialService companionCredentialService,
                                       CameraSettingsService cameraSettingsService, SessionService sessionService,
                                       ClockProvider clockProvider) {
        this.repository = repository;
        this.companionCredentialService = companionCredentialService;
        this.cameraSettingsService = cameraSettingsService;
        this.sessionService = sessionService;
        this.clockProvider = clockProvider;
    }

    /**
     * Asks the user's companion program to calibrate. 409 unless camera verification is on, the program
     * is paired and connected, and no camera-verified session is running: that one has the camera.
     */
    @Transactional
    public CameraSettingsResponse request(User user) {
        if (!cameraSettingsService.isEnabled(user)) {
            throw conflict("Turn camera verification on first.");
        }
        CompanionCredential credential = repository.findByUser(user)
                .orElseThrow(() -> conflict("Pair the companion program first."));
        if (!companionCredentialService.isConnected(user)) {
            throw conflict("The companion program is not connected. Start it, then calibrate again.");
        }
        boolean cameraInUse = sessionService.findCurrentSession(user)
                .filter(session -> session.getStatus() == SessionStatus.ACTIVE && session.isCameraVerification())
                .isPresent();
        if (cameraInUse) {
            throw conflict("The camera is in use for the running session. Calibrate between sessions.");
        }
        credential.requestCalibration(clockProvider.now());
        repository.save(credential);
        return cameraSettingsService.get(user);
    }

    /** What the program reports. It counts as hearing from it, like a heartbeat. */
    @Transactional
    public void report(User user, CompanionCalibrationRequest request) {
        companionCredentialService.recordSeen(user);
        CompanionCredential credential = repository.findByUser(user).orElseThrow();
        Instant now = clockProvider.now();
        switch (request.outcome()) {
            case STARTED -> credential.calibrationStarted(now);
            case CALIBRATED -> {
                if (request.calibratedAt() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "calibratedAt is required");
                }
                // A calibration cannot be from the future, whatever the program's clock says.
                credential.calibrated(request.calibratedAt().isAfter(now) ? now : request.calibratedAt());
            }
            case FAILED -> credential.calibrationFailed(
                    request.message() == null || request.message().isBlank() ? "Calibrating did not work." : request.message());
        }
        repository.save(credential);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
