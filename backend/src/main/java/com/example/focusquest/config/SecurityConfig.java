package com.example.focusquest.config;

import com.example.focusquest.security.AuthEntryPoint;
import com.example.focusquest.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthEntryPoint authEntryPoint;
    private final CorsConfigurationSource corsConfigurationSource;
    private final boolean h2ConsoleEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           AuthEntryPoint authEntryPoint,
                           @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource,
                           @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authEntryPoint = authEntryPoint;
        this.corsConfigurationSource = corsConfigurationSource;
        this.h2ConsoleEnabled = h2ConsoleEnabled;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authEntryPoint))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/setup", "/api/auth/setup-status", "/api/auth/login", "/error")
                            .permitAll();
                    if (h2ConsoleEnabled) {
                        // Local development tooling with its own login (see application.yml). Off by default:
                        // it gives full read and write access to the database, password hash included.
                        auth.requestMatchers("/h2-console/**").permitAll();
                    }
                    // The extension's own token (ROLE_EXTENSION) reaches only the endpoints it synchronizes with.
                    auth.requestMatchers("/api/extension/**").hasAnyRole("USER", "EXTENSION");
                    // Only the paired camera companion program (ROLE_COMPANION) may report what the
                    // camera saw; not even the signed-in web app can, and it reaches nothing else.
                    auth.requestMatchers("/api/companion/**").hasRole("COMPANION");
                    auth.anyRequest().hasRole("USER");
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (h2ConsoleEnabled) {
            // The console renders itself in frames. Every other response keeps the default DENY.
            http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));
        }

        return http.build();
    }
}
