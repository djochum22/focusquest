package com.example.focusquest.vision;

import com.example.focusquest.user.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Camera verification settings. Pure HTTP glue; the rules live in {@link CameraSettingsService}. */
@RestController
public class CameraSettingsController {

    private final CameraSettingsService cameraSettingsService;
    private final UserService userService;

    public CameraSettingsController(CameraSettingsService cameraSettingsService, UserService userService) {
        this.cameraSettingsService = cameraSettingsService;
        this.userService = userService;
    }

    @GetMapping("/api/me/camera-settings")
    public CameraSettingsResponse get(@AuthenticationPrincipal UserDetails principal) {
        return cameraSettingsService.get(userService.getByUsername(principal.getUsername()));
    }

    /** 400 when turning it on without accepting the current consent text. */
    @PutMapping("/api/me/camera-settings")
    public CameraSettingsResponse update(@AuthenticationPrincipal UserDetails principal,
                                         @Valid @RequestBody UpdateCameraSettingsRequest request) {
        return cameraSettingsService.update(userService.getByUsername(principal.getUsername()),
                request.enabled(), request.consentVersion(), request.verifyNewSessionsByDefault());
    }
}
