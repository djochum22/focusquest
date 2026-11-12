package com.example.focusquest.config;

import com.example.focusquest.security.AuthEntryPoint;
import com.example.focusquest.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

    /**
     * What the web app's pages may load and run: only what the backend itself serves, so text that
     * somehow got into a page as markup could not run as a script, nor send the sign-in token anywhere
     * else. Inline styles stay allowed (Vue sets them). {@code ipc:} and {@code ipc.localhost} are how
     * the page talks to the desktop app when it is open in that app's window.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "font-src 'self' data:",
            "connect-src 'self' ipc: http://ipc.localhost",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

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
                    // The desktop app's token (ROLE_DESKTOP) only reads what to block, and the session and streak
                    // progress its blocked page shows; it does not check in for sessions as the extension does.
                    auth.requestMatchers(HttpMethod.GET, "/api/extension/blocking-state", "/api/extension/current-session")
                            .hasAnyRole("USER", "EXTENSION", "DESKTOP");
                    // The extension's own token (ROLE_EXTENSION) reaches only the endpoints it synchronizes with.
                    auth.requestMatchers("/api/extension/**").hasAnyRole("USER", "EXTENSION");
                    // Only the paired camera companion program (ROLE_COMPANION) may report what the
                    // camera saw; not even the signed-in web app can, and it reaches nothing else.
                    auth.requestMatchers("/api/companion/**").hasRole("COMPANION");
                    auth.requestMatchers("/api/**").hasRole("USER");
                    // The web app's own files and routes (WebAppConfig); everything else still needs a token.
                    auth.requestMatchers(WebAppConfig::isWebAppRequest).permitAll();
                    auth.anyRequest().hasRole("USER");
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        http.headers(headers -> {
            // Nothing in a FocusQuest address (the blocked page's carries the task) is sent to other sites.
            headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN));
            if (h2ConsoleEnabled) {
                // The console renders itself in frames, with inline scripts, so it gets no content
                // security policy. Every other response keeps the default DENY.
                headers.frameOptions(frameOptions -> frameOptions.sameOrigin());
            } else {
                headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY));
            }
        });

        return http.build();
    }
}
