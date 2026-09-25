package com.example.e2e;

import com.example.focusquest.shared.time.ClockProvider;
import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Hooks the end-to-end suite needs and the real application must never have: wiping the database
 * between tests and moving the backend's clock forward, so a five-minute session can be completed
 * without waiting five minutes. Only active under the {@code e2e} profile, and only on the test
 * classpath ({@link E2eBackendApplication}). The package is deliberately outside
 * {@code com.example.focusquest}, so the application's component scan never finds these classes:
 * they are loaded only when {@link E2eBackendApplication} adds this configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("e2e")
public class E2eSupport {

    /**
     * Every test starts from 09:00 UTC on a fixed day, far from midnight, so a test that moves the
     * clock forward never splits its time across two daily streak periods.
     */
    static final Instant START_OF_TEST = Instant.parse("2026-03-10T09:00:00Z");

    /** The hooks are for the test runner, which has no account yet when it resets. */
    @Bean
    @Order(0)
    SecurityFilterChain e2eSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/e2e/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /** Registered as a member of this configuration, so it exists only when the configuration does. */
    @RestController
    @RequestMapping("/api/e2e")
    static class E2eController {

        private final Flyway flyway;
        private final ClockProvider clockProvider;

        E2eController(Flyway flyway, ClockProvider clockProvider) {
            this.flyway = flyway;
            this.clockProvider = clockProvider;
        }

        /** Drops and re-creates the whole schema, and starts the clock again from {@link #START_OF_TEST}. */
        @PostMapping("/reset")
        ResponseEntity<Void> reset() {
            flyway.clean();
            flyway.migrate();
            setClockTo(START_OF_TEST);
            return ResponseEntity.noContent().build();
        }

        /** Jumps the clock forward; it keeps ticking in real time from there. */
        @PostMapping("/clock/advance")
        ResponseEntity<Void> advance(@RequestParam("seconds") long seconds) {
            setClockTo(clockProvider.now().plusSeconds(seconds));
            return ResponseEntity.noContent().build();
        }

        private void setClockTo(Instant instant) {
            Clock system = Clock.systemUTC();
            clockProvider.setClock(Clock.offset(system, Duration.between(system.instant(), instant)));
        }
    }
}
