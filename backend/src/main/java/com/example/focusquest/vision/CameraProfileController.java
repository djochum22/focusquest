package com.example.focusquest.vision;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** What the camera checks for each task category. The same for every user, but sign-in is still required. */
@RestController
public class CameraProfileController {

    private final CameraProfiles cameraProfiles;

    public CameraProfileController(CameraProfiles cameraProfiles) {
        this.cameraProfiles = cameraProfiles;
    }

    @GetMapping("/api/camera/profiles")
    public List<CameraProfileResponse> profiles() {
        return cameraProfiles.all().stream().map(CameraProfileResponse::from).toList();
    }
}
