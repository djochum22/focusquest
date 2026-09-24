package com.example.focusquest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@ConfigurationProperties(prefix = "focusquest.jwt")
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    /** 48 random bytes is 64 Base64 characters, comfortably above HMAC-SHA256's 32-byte minimum. */
    private static final int GENERATED_SECRET_BYTES = 48;

    private String secret;
    private String generatedSecret;

    private long expirationMinutes = 60;

    /**
     * HMAC signing secret, at least 32 bytes, from {@code FOCUSQUEST_JWT_SECRET}. When none is
     * configured a random one is generated for this run: nothing that could be read from the source
     * code can then be used to forge a token. The price is that sign-ins end when the backend
     * restarts, so set the variable to keep them across restarts. The value is never logged.
     */
    public synchronized String getSecret() {
        if (secret != null && !secret.isBlank()) {
            return secret;
        }
        if (generatedSecret == null) {
            byte[] bytes = new byte[GENERATED_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
            generatedSecret = Base64.getEncoder().encodeToString(bytes);
            log.warn("FOCUSQUEST_JWT_SECRET is not set: using a temporary signing secret, "
                    + "so everyone is signed out whenever the backend restarts");
        }
        return generatedSecret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
