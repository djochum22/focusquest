package com.example.focusquest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.config.JwtConfig;
import com.example.focusquest.support.ApiIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The application's real security configuration, exercised end to end: which routes are public,
 * how tokens are checked, the CORS policy, response headers, and what reaches the logs.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecurityConfigurationIntegrationTest extends ApiIntegrationTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Autowired
    private JwtConfig jwtConfig;

    @Value("${spring.h2.console.enabled}")
    private boolean h2ConsoleEnabled;

    private static Stream<String[]> protectedEndpoints() {
        return Stream.of(
                new String[] {"GET", "/api/auth/me"},
                new String[] {"POST", "/api/focus-sessions"},
                new String[] {"GET", "/api/focus-sessions/current"},
                new String[] {"GET", "/api/focus-sessions/history"},
                new String[] {"GET", "/api/focus-sessions/planned"},
                new String[] {"DELETE", "/api/focus-sessions/1"},
                new String[] {"POST", "/api/focus-sessions/1/start"},
                new String[] {"POST", "/api/focus-sessions/1/pause"},
                new String[] {"POST", "/api/focus-sessions/1/resume"},
                new String[] {"POST", "/api/focus-sessions/1/complete"},
                new String[] {"POST", "/api/focus-sessions/1/abandon"},
                new String[] {"POST", "/api/focus-sessions/1/override"},
                new String[] {"GET", "/api/streaks/current"},
                new String[] {"GET", "/api/streak-configurations"},
                new String[] {"POST", "/api/streak-configurations"},
                new String[] {"PUT", "/api/streak-configurations/1"},
                new String[] {"GET", "/api/me/progression"},
                new String[] {"GET", "/api/me/streak-freezes"},
                new String[] {"POST", "/api/me/streak-freezes/purchase"},
                new String[] {"PUT", "/api/me/profile"},
                new String[] {"GET", "/api/export"},
                new String[] {"POST", "/api/me/data/restore"},
                new String[] {"DELETE", "/api/me/data"},
                new String[] {"GET", "/api/blocked-targets"},
                new String[] {"POST", "/api/blocked-targets"},
                new String[] {"PUT", "/api/blocked-targets/1"},
                new String[] {"DELETE", "/api/blocked-targets/1"},
                new String[] {"GET", "/api/allowlist-targets"},
                new String[] {"POST", "/api/auth/extension-token"},
                new String[] {"DELETE", "/api/auth/extension-token"},
                new String[] {"GET", "/api/extension/blocking-state"},
                new String[] {"GET", "/api/extension/current-session"},
                new String[] {"POST", "/api/extension/heartbeat"},
                // Unknown routes must not reveal whether they exist to an unauthenticated caller.
                new String[] {"GET", "/api/does-not-exist"},
                new String[] {"GET", "/actuator/env"},
                new String[] {"GET", "/h2-console/"},
                new String[] {"POST", "/h2-console/login.do"});
    }

    private static MockHttpServletRequestBuilder request(String method, String path) {
        return MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    private String tokenFor(String username, Date expiration, SecretKey key) {
        return Jwts.builder().subject(username).issuedAt(new Date()).expiration(expiration).signWith(key).compact();
    }

    // --- Authentication required ---

    @ParameterizedTest(name = "{0} {1} requires a token")
    @MethodSource("protectedEndpoints")
    void everyRouteExceptTheAuthBootstrapRejectsRequestsWithoutAToken(String method, String path) throws Exception {
        mockMvc.perform(request(method, path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void onlySetupSetupStatusAndLoginAreOpenWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/auth/setup-status")).andExpect(status().isOk());
        // Reaching the controller (and failing validation there) proves the route is not blocked by security.
        mockMvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- Token checks ---

    @Test
    void aValidTokenIsAccepted() throws Exception {
        String token = setUpAccount("doug", "UTC");

        getAs(token, "/api/auth/me").andExpect(status().isOk());
    }

    @Test
    void tokensThatAreExpiredTamperedForgedOrForAnUnknownUserAreRejected() throws Exception {
        String valid = setUpAccount("doug", "UTC");
        Date future = Date.from(Instant.now().plusSeconds(3600));

        String expired = tokenFor("doug", Date.from(Instant.now().minusSeconds(60)), secretKey());
        String wrongKey = tokenFor("doug", future, Keys.hmacShaKeyFor(new byte[32]));
        String unknownUser = tokenFor("nobody", future, secretKey());
        String unsigned = Jwts.builder().subject("doug").expiration(future).compact();
        // Flip a character in the middle of the signature so the payload is genuine but the signature is not.
        // (Not the last one: it carries padding bits, so some flips decode to the same signature bytes.)
        int flipAt = valid.length() - 10;
        char flipped = valid.charAt(flipAt);
        String tampered = valid.substring(0, flipAt) + (flipped == 'A' ? 'B' : 'A') + valid.substring(flipAt + 1);
        // Swap in another user's name while keeping the original signature.
        String[] parts = valid.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"admin\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        String payloadSwapped = parts[0] + "." + forgedPayload + "." + parts[2];

        for (String bad : List.of(expired, wrongKey, unknownUser, unsigned, tampered, payloadSwapped, "garbage",
                "a.b.c", "")) {
            getAs(bad, "/api/auth/me")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void onlyTheBearerSchemeIsAccepted() throws Exception {
        String token = setUpAccount("doug", "UTC");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Basic " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").queryParam("token", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void aBadTokenIsRefusedEvenOnAPublicRouteWithoutBreakingIt() throws Exception {
        // A stale token in the browser must not stop the login page from working.
        mockMvc.perform(get("/api/auth/setup-status").header("Authorization", "Bearer stale.token.value"))
                .andExpect(status().isOk());
    }

    // --- Login and account setup ---

    @Test
    void loginFailuresGiveTheSameAnswerForAWrongPasswordAndAnUnknownUser() throws Exception {
        setUpAccount("doug", "UTC");

        String wrongPassword = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "doug", "password", "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "nobody", "password", "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownUser);
        assertThat(wrongPassword).contains("Invalid username or password").doesNotContain("doug");
    }

    @Test
    void loginIssuesATokenWithoutEverExposingThePasswordOrItsHash() throws Exception {
        setUpAccount("doug", "UTC");

        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "doug", "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(jwtConfig.getExpirationMinutes() * 60))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(PASSWORD).doesNotContain("$2a$").doesNotContainIgnoringCase("hash");
        String token = JsonPath.read(body, "$.token");
        String me = getAs(token, "/api/auth/me").andReturn().getResponse().getContentAsString();
        assertThat(me).doesNotContain("$2a$").doesNotContainIgnoringCase("password");
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() throws Exception {
        setUpAccount("doug", "UTC");

        String stored = userRepository.findByUsername("doug").orElseThrow().getPasswordHash();

        assertThat(stored).startsWith("$2").doesNotContain(PASSWORD);
    }

    @Test
    void setupWorksOnlyOnceEvenWithDifferentCredentials() throws Exception {
        setUpAccount("doug", "UTC");

        mockMvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "intruder", "password", PASSWORD, "displayName", "X", "timezone", "UTC")))
                .andExpect(status().isConflict());
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void setupRejectsWeakOrOversizedInput() throws Exception {
        mockMvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "doug", "password", "short", "displayName", "D", "timezone", "UTC")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                // The message names the field, and never echoes the rejected value back.
                .andExpect(jsonPath("$.message").value(not(containsString("short\""))));
        mockMvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "x".repeat(101), "password", PASSWORD, "displayName", "D",
                                "timezone", "UTC")))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void anInvalidTimeZoneIsRefusedAtSetupInsteadOfBreakingEveryLaterRequest() throws Exception {
        mockMvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "doug", "password", PASSWORD, "displayName", "D",
                                "timezone", "Not/AZone")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown time zone"));
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void passwordsBeyondBcryptsLimitAreRefusedWithAClientErrorAtSetupAndLogin() throws Exception {
        // BCrypt reads only 72 bytes and its encoder throws on more; that must never surface as a 500.
        // 72 characters of "é" is 144 bytes, so the character limit alone would not catch it.
        for (String tooLong : List.of("p".repeat(73), "é".repeat(50), "p".repeat(100))) {
            int status = mockMvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON)
                            .content(json("username", "doug", "password", tooLong, "displayName", "D",
                                    "timezone", "UTC")))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as("setup with %d bytes", tooLong.getBytes(StandardCharsets.UTF_8).length)
                    .isEqualTo(400);
        }
        assertThat(userRepository.count()).isZero();

        setUpAccount("doug", "UTC");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "doug", "password", "p".repeat(100))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void aPasswordExactlyAtTheLimitWorks() throws Exception {
        String atLimit = "p".repeat(72);
        mockMvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "doug", "password", atLimit, "displayName", "D", "timezone", "UTC")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "doug", "password", atLimit)))
                .andExpect(status().isOk());
    }

    // --- CORS ---

    @Test
    void preflightFromTheFrontendOriginIsAllowedWithoutCredentials() throws Exception {
        mockMvc.perform(options("/api/focus-sessions")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN))
                // Auth is a bearer header, never a cookie, so credentialed cross-origin requests are not needed.
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().exists("Access-Control-Max-Age"));
    }

    @Test
    void actualRequestsFromTheFrontendOriginGetTheCorsHeader() throws Exception {
        mockMvc.perform(get("/api/auth/setup-status").header("Origin", FRONTEND_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN));
    }

    @ParameterizedTest(name = "origin {0} is refused")
    @MethodSource("foreignOrigins")
    void otherOriginsAreRefused(String origin) throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(get("/api/auth/setup-status").header("Origin", origin))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private static Stream<String> foreignOrigins() {
        return Stream.of(
                "https://evil.example",
                "http://localhost:5174",                    // right host, wrong port
                "https://localhost:5173",                   // right host and port, wrong scheme
                "http://localhost:5173.evil.example",       // allowed origin as a prefix
                "http://127.0.0.1:5173",
                "chrome-extension://someotherextensionid",
                "null");
    }

    @Test
    void preflightWithAnUnlistedHeaderOrMethodIsRefused() throws Exception {
        mockMvc.perform(options("/api/focus-sessions")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-injected-header"))
                .andExpect(status().isForbidden());
        mockMvc.perform(options("/api/focus-sessions")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "TRACE"))
                .andExpect(status().isForbidden());
    }

    // --- Headers ---

    @Test
    void responsesCarryProtectiveHeadersAndAreNotCacheable() throws Exception {
        String token = setUpAccount("doug", "UTC");

        getAs(token, "/api/auth/me")
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    // --- Local development tooling ---

    @Test
    void theH2ConsoleIsSwitchedOffUnlessTheDeveloperAsksForIt() {
        // MockMvc cannot reach the console's own servlet, so check the switch. The 401 for /h2-console
        // above shows security no longer lets the path through either.
        assertThat(h2ConsoleEnabled).isFalse();
    }

    // --- Logging ---

    @Test
    void secretsNeverReachTheLogs(CapturedOutput output) throws Exception {
        String secretPassword = "very-secret-passphrase-" + System.nanoTime();
        String token = JsonPath.read(mockMvc.perform(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "doug", "password", secretPassword, "displayName", "Doug",
                                "timezone", "UTC")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.token");
        String hash = userRepository.findByUsername("doug").orElseThrow().getPasswordHash();

        // Exercise success and failure paths that could plausibly log a credential.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("username", "doug", "password", secretPassword)));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("username", "doug", "password", "wrong-" + secretPassword)));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token + "x"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{broken " + secretPassword));
        postJsonAs(token, "/api/focus-sessions", "{\"plannedFocusMinutes\": 1, \"taskDescription\": \"" + secretPassword + "\"}");
        perform(token, delete("/api/me/data"));

        assertThat(output.getAll())
                .doesNotContain(secretPassword)
                .doesNotContain(token)
                .doesNotContain(hash)
                .doesNotContain(jwtConfig.getSecret());
    }

    @Test
    void thePlaceholderSigningSecretIsNotUsedWhenNoneIsConfigured() {
        // The repository ships a placeholder; running with it would let anyone who read the source forge a token.
        assertThat(jwtConfig.getSecret())
                .hasSizeGreaterThanOrEqualTo(32)
                .doesNotContain("CHANGE_ME")
                .doesNotContain("placeholder");
    }
}
