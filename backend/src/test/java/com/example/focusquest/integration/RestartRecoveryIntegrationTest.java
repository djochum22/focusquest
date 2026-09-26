package com.example.focusquest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.focusquest.FocusQuestApplication;
import com.example.focusquest.auth.AuthService;
import com.example.focusquest.auth.SetupRequest;
import com.example.focusquest.blocking.BlockingService;
import com.example.focusquest.progression.ProgressionService;
import com.example.focusquest.session.BlockingState;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Stops and starts the application against the same database file, as closing and reopening it (or
 * a crash) does. Nothing is kept in memory between the two runs, so whatever the second run sees
 * came from the database.
 */
class RestartRecoveryIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-03-10T09:00:00Z");
    private static final String USERNAME = "doug";

    @TempDir
    Path dataDirectory;

    /** One run of the application with its clock frozen at {@code at}. The context is closed afterwards. */
    private void run(Instant at, Consumer<App> body) {
        String url = "jdbc:h2:file:" + dataDirectory.resolve("focusquest");
        // Command-line arguments, not builder properties: those are only defaults, and application.yml
        // would point the run at the developer's own database.
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(FocusQuestApplication.class)
                .run("--spring.datasource.url=" + url, "--server.port=0")) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isEqualTo(url);
            context.getBean(ClockProvider.class).setClock(Clock.fixed(at, ZoneOffset.UTC));
            body.accept(new App(context));
        }
    }

    private record App(ConfigurableApplicationContext context) {

        <T> T get(Class<T> type) {
            return context.getBean(type);
        }

        User user() {
            return get(UserService.class).getByUsername(USERNAME);
        }

        void setClock(Instant at) {
            get(ClockProvider.class).setClock(Clock.fixed(at, ZoneOffset.UTC));
        }
    }

    private static void setUp(App app) {
        app.get(AuthService.class).setup(new SetupRequest(USERNAME, "correct-horse-battery", "Doug", "UTC"));
    }

    private static FocusSession startSession(App app, int plannedMinutes) {
        SessionService sessions = app.get(SessionService.class);
        FocusSession planned = sessions.createSession(app.user(), "Write", TaskMode.TASK_REQUIRED,
                TaskCategory.WRITING, plannedMinutes);
        return sessions.startSession(planned.getId(), USERNAME);
    }

    @Test
    void completedWorkAndAPausedSessionSurviveARestart() {
        long[] pausedId = new long[1];
        run(BASE, app -> {
            setUp(app);
            FocusSession done = startSession(app, 30);
            app.setClock(BASE.plusSeconds(30 * 60));
            app.get(SessionService.class).completeSession(done.getId(), USERNAME);

            app.setClock(BASE.plusSeconds(40 * 60));
            FocusSession paused = startSession(app, 20);
            app.setClock(BASE.plusSeconds(45 * 60));
            app.get(SessionService.class).pauseSession(paused.getId(), USERNAME);
            pausedId[0] = paused.getId();
        });

        run(BASE.plusSeconds(50 * 60), app -> {
            User user = app.user();
            assertThat(app.get(ProgressionService.class).getSummary(user).totalXp()).isEqualTo(40);
            assertThat(app.get(StreakService.class).getCurrentStreakLength(user, StreakPeriodType.DAILY)).isEqualTo(1);

            SessionService sessions = app.get(SessionService.class);
            FocusSession current = sessions.findCurrentSession(user).orElseThrow();
            assertThat(current.getId()).isEqualTo(pausedId[0]);
            assertThat(current.getStatus()).isEqualTo(SessionStatus.PAUSED);
            assertThat(current.getActiveFocusSeconds()).isEqualTo(5 * 60);
            assertThat(app.get(BlockingService.class).findEnforcingSession(user)).isPresent();

            // The pause picks up where it was: resuming finalizes the 5 minutes paused across the restart.
            FocusSession resumed = sessions.resumeSession(current.getId(), USERNAME);
            assertThat(resumed.getFinalizedPausedSeconds()).isEqualTo(5 * 60);
            app.setClock(BASE.plusSeconds(65 * 60));
            FocusSession completed = sessions.completeSession(current.getId(), USERNAME);
            assertThat(completed.getActiveFocusSeconds()).isEqualTo(20 * 60);
        });
    }

    @Test
    void aSessionTheExtensionWasWatchingWhenTheAppWentDownIsInterruptedAtItsLastHeartbeat() {
        long[] sessionId = new long[1];
        run(BASE, app -> {
            setUp(app);
            FocusSession session = startSession(app, 30);
            sessionId[0] = session.getId();
            app.setClock(BASE.plusSeconds(4 * 60));
            app.get(BlockingService.class).heartbeat(app.user());
            // The app goes down 2 minutes later, without a clean end to the session.
            app.setClock(BASE.plusSeconds(6 * 60));
        });

        run(BASE.plusSeconds(60 * 60), app -> {
            User user = app.user();
            FocusSession current = app.get(SessionService.class).findCurrentSession(user).orElseThrow();

            assertThat(current.getId()).isEqualTo(sessionId[0]);
            assertThat(current.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
            assertThat(current.getBlockingState()).isEqualTo(BlockingState.TECHNICAL_RELEASE);
            // Time up to the last heartbeat counts; the unverified time after it does not.
            assertThat(current.getActiveFocusSeconds()).isEqualTo(4 * 60);
            assertThat(app.get(StreakService.class).findCurrentPeriod(user, StreakPeriodType.DAILY).orElseThrow()
                    .getQualifyingSeconds()).isEqualTo(4 * 60);
            assertThat(app.get(BlockingService.class).findEnforcingSession(user)).isEmpty();

            FocusSession resumed = app.get(SessionService.class).resumeSession(current.getId(), USERNAME);
            assertThat(resumed.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        });
    }

    @Test
    void aSessionRunWithoutTheExtensionKeepsRunningAcrossARestart() {
        run(BASE, app -> {
            setUp(app);
            startSession(app, 30);
        });

        run(BASE.plusSeconds(10 * 60), app -> {
            SessionService sessions = app.get(SessionService.class);
            FocusSession current = sessions.findCurrentSession(app.user()).orElseThrow();

            // Never watched by the extension, so there is nothing to verify against: the time counts.
            assertThat(current.getStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(current.activeSecondsAt(BASE.plusSeconds(10 * 60))).isEqualTo(10 * 60);
        });
    }
}
