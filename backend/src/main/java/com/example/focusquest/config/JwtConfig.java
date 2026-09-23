package com.example.focusquest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "focusquest.jwt")
public class JwtConfig {

    /**
     * HMAC-SHA256 signing secret. Must be at least 32 bytes. Provided through
     * configuration/environment variables; never commit a real secret to Git.
     */
    private String secret;

    private long expirationMinutes = 60;

    public String getSecret() {
        return secret;
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
