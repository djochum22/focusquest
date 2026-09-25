package com.example.focusquest.auth;

import java.time.Instant;

/** A freshly issued extension token. This is the only time the token itself is available. */
public record IssuedExtensionCredential(String token, Instant createdAt) {
}
