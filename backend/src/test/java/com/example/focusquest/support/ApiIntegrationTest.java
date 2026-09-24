package com.example.focusquest.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.blocking.AllowlistTargetRepository;
import com.example.focusquest.blocking.BlockedTargetRepository;
import com.example.focusquest.progression.ExperienceTransactionRepository;
import com.example.focusquest.progression.GemTransactionRepository;
import com.example.focusquest.session.FocusSessionRepository;
import com.example.focusquest.session.SessionPauseRepository;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakConfigurationRepository;
import com.example.focusquest.streak.StreakContributionRepository;
import com.example.focusquest.streak.StreakPeriodRepository;
import com.example.focusquest.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base for tests that drive the whole application through HTTP: the real security filter chain,
 * controllers, services and an in-memory H2 database migrated by Flyway. Nothing is mocked.
 *
 * <p>Every test starts from an empty database (so first-launch setup is available again) and a
 * clock frozen at {@link #BASE}, which a test moves forward with {@link #advance}. The JWT
 * service reads the system clock, so tokens keep working however far the application clock moves.
 * All subclasses share one Spring context, and therefore one database, so they must not run in
 * parallel.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:api-integration;DB_CLOSE_DELAY=-1")
// No request/response dumps on failure: they would put tokens in the captured output that log tests inspect.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
public abstract class ApiIntegrationTest {

    /** A Tuesday, 09:00 UTC. */
    protected static final Instant BASE = Instant.parse("2026-03-10T09:00:00Z");

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ClockProvider clockProvider;

    @Autowired
    private StreakContributionRepository streakContributionRepository;
    @Autowired
    private SessionPauseRepository sessionPauseRepository;
    @Autowired
    private StreakPeriodRepository streakPeriodRepository;
    @Autowired
    private FocusSessionRepository focusSessionRepository;
    @Autowired
    private StreakConfigurationRepository streakConfigurationRepository;
    @Autowired
    private ExperienceTransactionRepository experienceTransactionRepository;
    @Autowired
    private GemTransactionRepository gemTransactionRepository;
    @Autowired
    private BlockedTargetRepository blockedTargetRepository;
    @Autowired
    private AllowlistTargetRepository allowlistTargetRepository;
    @Autowired
    protected UserRepository userRepository;

    private Clock originalClock;
    private Instant now;

    @BeforeEach
    void resetDatabaseAndClock() {
        // Children before parents, in foreign-key order.
        streakContributionRepository.deleteAllInBatch();
        sessionPauseRepository.deleteAllInBatch();
        streakPeriodRepository.deleteAllInBatch();
        focusSessionRepository.deleteAllInBatch();
        streakConfigurationRepository.deleteAllInBatch();
        experienceTransactionRepository.deleteAllInBatch();
        gemTransactionRepository.deleteAllInBatch();
        blockedTargetRepository.deleteAllInBatch();
        allowlistTargetRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        originalClock = clockProvider.getClock();
        now = BASE;
        clockProvider.setClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    @AfterEach
    void restoreClock() {
        clockProvider.setClock(originalClock);
    }

    protected void advance(long seconds) {
        now = now.plusSeconds(seconds);
        clockProvider.setClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    protected void advanceTo(Instant instant) {
        now = instant;
        clockProvider.setClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Runs first-launch setup and returns the bearer token of the account it creates. */
    protected String setUpAccount(String username, String timezone) throws Exception {
        String body = json("username", username, "password", PASSWORD, "displayName", "Test User", "timezone", timezone);
        String response = mockMvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    protected ResultActions perform(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    protected ResultActions getAs(String token, String path) throws Exception {
        return perform(token, get(path));
    }

    protected ResultActions postAs(String token, String path) throws Exception {
        return perform(token, post(path));
    }

    protected ResultActions postJsonAs(String token, String path, String body) throws Exception {
        return perform(token, post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Creates a session, starts it and returns its id. */
    protected long createAndStartSession(String token, int plannedMinutes) throws Exception {
        long id = createSession(token, plannedMinutes);
        postAs(token, "/api/focus-sessions/" + id + "/start").andExpect(status().isOk());
        return id;
    }

    protected long createSession(String token, int plannedMinutes) throws Exception {
        String response = postJsonAs(token, "/api/focus-sessions", json(
                "taskDescription", "Write the report", "taskMode", "TASK_REQUIRED", "taskCategory", "WRITING",
                "plannedFocusMinutes", plannedMinutes))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    /** Builds a flat JSON object from alternating keys and values; numbers and booleans stay unquoted. */
    protected static String json(Object... keysAndValues) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            Object value = keysAndValues[i + 1];
            out.append('"').append(keysAndValues[i]).append("\":");
            out.append(value instanceof Number || value instanceof Boolean ? value : "\"" + value + "\"");
        }
        return out.append('}').toString();
    }
}
