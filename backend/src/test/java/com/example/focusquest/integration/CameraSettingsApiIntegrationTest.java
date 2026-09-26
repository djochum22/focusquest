package com.example.focusquest.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.support.ApiIntegrationTest;
import com.example.focusquest.vision.CameraSettings;
import com.example.focusquest.vision.CameraSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Consent to camera verification and its settings, over HTTP. */
class CameraSettingsApiIntegrationTest extends ApiIntegrationTest {

    private static final String SETTINGS = "/api/me/camera-settings";

    @Autowired
    private CameraSettingsRepository cameraSettingsRepository;

    private ResultActions update(String token, String body) throws Exception {
        return perform(token, put(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void cameraVerificationIsOffUntilTheUserConsents() throws Exception {
        String token = setUpAccount("doug", "UTC");

        getAs(token, SETTINGS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.consentVersion").value(1))
                .andExpect(jsonPath("$.consentedAt").doesNotExist())
                .andExpect(jsonPath("$.verifyNewSessionsByDefault").value(true));
    }

    @Test
    void turningItOnRecordsConsentToTheCurrentText() throws Exception {
        String token = setUpAccount("doug", "UTC");

        update(token, json("enabled", true, "consentVersion", 1, "verifyNewSessionsByDefault", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.consentedAt").value(BASE.toString()));

        advance(3600);
        // Changing only the default keeps the original consent.
        update(token, json("enabled", true, "verifyNewSessionsByDefault", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.consentedAt").value(BASE.toString()))
                .andExpect(jsonPath("$.verifyNewSessionsByDefault").value(false));
        getAs(token, "/api/export")
                .andExpect(jsonPath("$.schemaVersion").value("2.2"))
                .andExpect(jsonPath("$.cameraSettings.consentVersion").value(1))
                .andExpect(jsonPath("$.cameraSettings.verifyNewSessions").value(false));
    }

    @Test
    void turningItOnWithoutAcceptingTheCurrentTextIsRefused() throws Exception {
        String token = setUpAccount("doug", "UTC");

        update(token, json("enabled", true, "verifyNewSessionsByDefault", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Read and accept the current camera consent text to turn camera verification on"));
        update(token, json("enabled", true, "consentVersion", 0, "verifyNewSessionsByDefault", true))
                .andExpect(status().isBadRequest());

        getAs(token, SETTINGS).andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void turningItOffWithdrawsConsent() throws Exception {
        String token = setUpAccount("doug", "UTC");
        update(token, json("enabled", true, "consentVersion", 1, "verifyNewSessionsByDefault", true))
                .andExpect(status().isOk());

        update(token, json("enabled", false, "verifyNewSessionsByDefault", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.consentedAt").doesNotExist());

        // Turning it back on needs consent again.
        update(token, json("enabled", true, "verifyNewSessionsByDefault", true)).andExpect(status().isBadRequest());
    }

    @Test
    void consentToAnOlderConsentTextNoLongerCounts() throws Exception {
        String token = setUpAccount("doug", "UTC");
        cameraSettingsRepository.save(new CameraSettings(userRepository.findAll().getFirst(), 0, BASE, true));

        getAs(token, SETTINGS)
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.consentedAt").doesNotExist());
        update(token, json("enabled", true, "verifyNewSessionsByDefault", true)).andExpect(status().isBadRequest());
    }

    @Test
    void theRequestMustSayWhetherToTurnItOn() throws Exception {
        String token = setUpAccount("doug", "UTC");

        update(token, json("verifyNewSessionsByDefault", true)).andExpect(status().isBadRequest());
    }
}
