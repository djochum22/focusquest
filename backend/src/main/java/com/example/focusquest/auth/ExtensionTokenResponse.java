package com.example.focusquest.auth;

import java.time.Instant;

public record ExtensionTokenResponse(String token, Instant createdAt) {

    static ExtensionTokenResponse from(IssuedExtensionCredential credential) {
        return new ExtensionTokenResponse(credential.token(), credential.createdAt());
    }
}
