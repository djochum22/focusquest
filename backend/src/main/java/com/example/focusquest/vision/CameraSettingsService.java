package com.example.focusquest.vision;

import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Consent to camera verification and its settings. Camera verification is off until the user
 * accepts the current consent text; consent to an older version no longer counts, so the text is
 * read again whenever it changes. Turning it off withdraws consent.
 */
@Service
public class CameraSettingsService {

    /** Bump when the consent text shown in the web app changes in substance. */
    public static final int CONSENT_VERSION = 1;

    /** New sessions use the camera by default once it is turned on, until the user says otherwise. */
    private static final boolean DEFAULT_VERIFY_NEW_SESSIONS = true;

    private final CameraSettingsRepository cameraSettingsRepository;
    private final ClockProvider clockProvider;

    public CameraSettingsService(CameraSettingsRepository cameraSettingsRepository, ClockProvider clockProvider) {
        this.cameraSettingsRepository = cameraSettingsRepository;
        this.clockProvider = clockProvider;
    }

    @Transactional(readOnly = true)
    public CameraSettingsResponse get(User user) {
        return cameraSettingsRepository.findByUser(user)
                .map(this::toResponse)
                .orElse(new CameraSettingsResponse(false, CONSENT_VERSION, null, DEFAULT_VERIFY_NEW_SESSIONS));
    }

    @Transactional
    public CameraSettingsResponse update(User user, boolean enabled, Integer consentVersion,
                                         boolean verifyNewSessionsByDefault) {
        CameraSettings settings = cameraSettingsRepository.findByUser(user)
                .orElseGet(() -> new CameraSettings(user, null, null, DEFAULT_VERIFY_NEW_SESSIONS));
        if (!enabled) {
            settings.withdrawConsent();
        } else if (!settings.isEnabled(CONSENT_VERSION)) {
            if (consentVersion == null || consentVersion != CONSENT_VERSION) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Read and accept the current camera consent text to turn camera verification on");
            }
            settings.giveConsent(CONSENT_VERSION, clockProvider.now());
        }
        settings.setVerifyNewSessions(verifyNewSessionsByDefault);
        return toResponse(cameraSettingsRepository.save(settings));
    }

    /** Whether the user currently has camera verification turned on, with consent to the current text. */
    @Transactional(readOnly = true)
    public boolean isEnabled(User user) {
        return cameraSettingsRepository.findByUser(user).map(s -> s.isEnabled(CONSENT_VERSION)).orElse(false);
    }

    private CameraSettingsResponse toResponse(CameraSettings settings) {
        boolean enabled = settings.isEnabled(CONSENT_VERSION);
        return new CameraSettingsResponse(enabled, CONSENT_VERSION, enabled ? settings.getConsentedAt() : null,
                settings.isVerifyNewSessions());
    }
}
