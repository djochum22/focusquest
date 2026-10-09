package com.example.focusquest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "focusquest.cors")
public class CorsConfig {

    private List<String> allowedOrigins = List.of();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * Firefox and Safari give each install of the extension a random id, so its origin cannot be listed in
     * advance as Chrome's pinned one is. These patterns admit any of them, and only on the extension's own
     * endpoints, which also need its bearer token: CORS guards nothing more there, as no cookie is involved.
     */
    static final List<String> EXTENSION_ORIGIN_PATTERNS = List.of("moz-extension://*", "safari-web-extension://*");

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Exact origins only: patterns would let a stray "*" in the configuration open the API to any site.
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Authentication is a bearer header, never a cookie, so credentialed cross-origin requests are not needed.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));

        CorsConfiguration extensionConfiguration = new CorsConfiguration(configuration);
        extensionConfiguration.setAllowedOriginPatterns(EXTENSION_ORIGIN_PATTERNS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // The first pattern that matches wins, so the narrower one goes first.
        source.registerCorsConfiguration("/api/extension/**", extensionConfiguration);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
