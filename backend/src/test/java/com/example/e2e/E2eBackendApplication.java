package com.example.e2e;

import com.example.focusquest.FocusQuestApplication;
import org.springframework.boot.SpringApplication;

/**
 * Runs the real backend for the Playwright end-to-end suite, with {@link E2eSupport} added. It lives
 * in the test sources, so the reset and clock hooks can never reach the application that ships.
 * Started by {@code ./gradlew bootTestRun --args=--spring.profiles.active=e2e} (see e2e/README.md).
 */
public class E2eBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(FocusQuestApplication::main).with(E2eSupport.class).run(args);
    }
}
