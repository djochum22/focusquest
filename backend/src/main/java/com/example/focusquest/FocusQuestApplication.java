package com.example.focusquest;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FocusQuestApplication {

    public static void main(String[] args) {
        SpringApplication.run(FocusQuestApplication.class, args);
    }

    /**
     * A single injectable clock so time-dependent services (exports, sessions, streak
     * boundaries) can be tested with {@link Clock#fixed} instead of the wall clock.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
