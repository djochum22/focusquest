**FocusQuest**

**Technical Architecture Specification**

**Document status:** Working draft

**Project type:** Local-first productivity web application with a Chrome browser extension

**MVP:** One local user, local data storage, secure backend API, Chrome website blocking, focus sessions, streaks, XP, and later gems/streak freezes.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**1\. Purpose**

This document defines the technical architecture for FocusQuest. It describes the application components, responsibilities, communication paths, security design, database model, API direction, project structure, and testing approach.

FocusQuest is a local-first productivity application. The user creates focus sessions, chooses websites or URL paths to block, tracks qualifying work toward a daily or weekly streak, and receives XP for completing sustained focus sessions.

The system separates:

- **Streak progress:** Accumulated qualifying time across sessions.
- **Session completion:** Completion of a planned amount of active focus time.
- **Browser enforcement:** Blocking selected websites while enforcement is active.
- **Rewards:** XP, later gems, and streak freezes.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**2\. Architecture principles**

The architecture should follow these principles:

- Use a modular monolith rather than microservices.
- Keep business rules in backend services rather than controllers or UI components.
- Keep the Spring Boot backend as the source of truth for persistent session, streak, XP, and blocking state.
- Store data locally during the MVP.
- Design for secure access from both the Vue frontend and the Chrome extension.
- Isolate browser-specific behavior inside the Chrome extension.
- Make state transitions explicit and testable.
- Record XP, gem, and streak changes as transactions or contributions to prevent duplicate rewards.
- Use immutable completed-session data.
- Prepare the database layer for a later migration from H2 to PostgreSQL.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**3\. High-level architecture**

```
+-----------------------------+
| Vue.js Frontend             |
| Vue 3 + TypeScript + Vite   |
| Session UI, dashboard,      |
| streaks, settings, history  |
+--------------+--------------+
               |
               | REST/JSON + Bearer token
               v
+-----------------------------+
| Spring Boot Backend         |
| Java 21, modular monolith   |
| Business logic, validation, |
| security, persistence, APIs |
+--------------+--------------+
               |
               | JPA / Hibernate
               v
+-----------------------------+
| H2 Database                 |
| Local persistent storage    |
| PostgreSQL later            |
+-----------------------------+

+-----------------------------+
| Chrome Extension            |
| TypeScript, Manifest V3     |
| URL blocking, blocked page, |
| backend synchronization     |
+--------------+--------------+
               |
               | REST/JSON + Bearer token
               v
+-----------------------------+
| Spring Boot Backend         |
+-----------------------------+
```

**Component responsibilities**

| Component           | Responsibilities                                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Vue frontend        | User interface, forms, dashboard, session controls, settings, history, progress display                                        |
| ---                 | ---                                                                                                                            |
| Spring Boot backend | Authentication, authorization, session state, time calculation, streak calculation, rewards, validation, persistence, REST API |
| ---                 | ---                                                                                                                            |
| H2 database         | Local storage for user, sessions, pauses, streaks, block rules, XP, gems, freezes, configuration                               |
| ---                 | ---                                                                                                                            |
| Chrome extension    | URL matching, domain/path blocking, allowlist precedence, blocked page, extension-to-backend synchronization                   |
| ---                 | ---                                                                                                                            |

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**4\. Technology stack**

**Backend**

- Java 21.
- Spring Boot 4.
- Spring Web.
- Spring Data JPA.
- Spring Validation.
- Spring Security.
- H2 database for local development and MVP persistence.
- Flyway or Liquibase for database migrations; Flyway is recommended.
- Gradle (Kotlin DSL).
- JUnit 5.
- Mockito.
- Spring Boot test support.

**Frontend**

- Vue 3.
- TypeScript.
- Vite.
- Vue Router.
- Pinia for global client state where useful.
- Axios or Fetch API for REST communication.
- Vitest for unit and component tests.
- Playwright for future end-to-end tests.

**Chrome extension**

- TypeScript.
- Chrome Extension Manifest V3.
- Service-worker background process.
- Chrome storage for extension-local state and authentication token, using the appropriate storage area.
- Chrome declarative network request APIs where appropriate.
- An extension-hosted blocked page.

**Database migration**

- H2 for MVP.
- PostgreSQL for a later multi-user or deployed version.
- Flyway migrations from the beginning so database evolution is repeatable.

**Future computer vision**

Computer vision is outside the MVP. If added later:

- Python.
- OpenCV.
- MediaPipe.
- PyTorch only if a custom model becomes necessary.
- Local-first processing; no raw video upload or default storage.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**5\. Local execution model**

During local development:

```
Vue dev server:        http://localhost:5173
Spring Boot backend:   http://localhost:8080
Chrome extension:      loaded unpacked in Chrome
Database:              local H2 database file
```

The Vue application and Chrome extension communicate with the local Spring Boot backend through authenticated REST calls.

The backend should bind only to localhost in the MVP unless there is a deliberate need to expose it on a local network.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**6\. Security architecture**

Spring Security is part of the MVP architecture.

**Security goals**

- Protect all application API endpoints from unauthenticated access.
- Support one local user account.
- Secure frontend-to-backend communication.
- Secure Chrome-extension-to-backend communication.
- Keep credentials and tokens out of source control.
- Provide a foundation for later multi-user support.

**Authentication model**

The recommended approach is token-based authentication using JWT bearer tokens.

Flow:

```
1. User opens the Vue application.
2. Frontend calls GET /api/auth/setup-status. If setupRequired is true it shows the setup screen
   (first launch); otherwise it shows the login screen.
3. User enters local username and password.
4. Frontend sends credentials to /api/auth/login.
5. Backend validates password using BCrypt.
6. Backend creates a signed JWT.
7. Frontend stores the token securely for the local application session.
8. Frontend sends Authorization: Bearer <token> with API requests.
9. Chrome extension stores and uses the same token for extension API calls.
```

On first launch the user creates the account through POST /api/auth/setup instead of step 4. Setup
returns the same response as login, so the user is signed in immediately.

**User account model**

The MVP has one local user account. The application should show a setup/login flow rather than relying on an insecure hard-coded account.

A local user account contains:

```
User
- id
- username
- passwordHash
- displayName
- timezone
- createdAt
```

Passwords must never be stored in plaintext. Passwords must be hashed with BCrypt.

**JWT requirements**

- JWT signing secret must be provided through configuration or environment variables.
- The secret must not be committed to Git.
- JWTs should have an expiration time.
- The backend must validate signature, expiration, and subject.
- JWT authentication must populate Spring Security's authentication context.
- Expired or invalid tokens must return HTTP 401.

**Endpoint access policy**

<div class="joplin-table-wrapper"><table><thead><tr><th><p>Endpoint group</p></th><th><p>Access policy</p></th></tr><tr><th><pre><code>/api/auth/login</code></pre></th><th><p>Public</p></th></tr><tr><th><pre><code>/api/auth/setup</code></pre></th><th><p>Public only when no local user exists</p></th></tr><tr><th><pre><code>/api/auth/setup-status</code></pre></th><th><p>Public; reveals only whether first-launch setup is still pending</p></th></tr><tr><th><p>/api/auth/** remaining protected operations</p></th><th><p>Authenticated where applicable</p></th></tr><tr><th><pre><code>/api/**</code></pre></th><th><p>Authenticated</p></th></tr><tr><th><p>Extension endpoints</p></th><th><p>Authenticated with bearer token</p></th></tr><tr><th><p>Health endpoint, if exposed</p></th><th><p>Local-only or public depending on configuration</p></th></tr></thead></table></div>

**CSRF**

For a stateless bearer-token REST API, CSRF protection may be disabled because browser cookies are not used for authentication. If the project later switches to session-cookie authentication, CSRF protection must be enabled and handled correctly.

**CORS**

CORS must be explicit and restrictive.

Development origins may include:

```
http://localhost:5173
chrome-extension://<extension-id>
```

The extension ID may differ during development and production packaging. It should be configured rather than hard-coded where possible.

Allowed methods should be limited to:

```
GET, POST, PUT, PATCH, DELETE, OPTIONS
```

Allowed request headers should include only what is needed:

```
Authorization, Content-Type
```

**Token storage**

- The frontend should avoid exposing long-lived tokens unnecessarily.
- The Vue frontend keeps the token in `sessionStorage`, so it is discarded when the browser tab closes. It also records the token's expiry time and treats an expired token as signed out without calling the backend. After a page reload only the token survives; the user profile is re-fetched from `GET /api/auth/me`, and a rejected token signs the user out.
- The Chrome extension should use chrome.storage rather than plain page local storage for extension-owned token storage.
- The application should not log bearer tokens.
- The extension must not inject tokens into ordinary website pages.

**Security-related future work**

- Password-change endpoint.
- Token refresh or short-lived access tokens.
- Local device registration for the extension.
- Rate limiting.
- Audit logging for sensitive actions.
- Role-based authorization if multiple users are introduced.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**7\. Backend architecture**

**Backend responsibilities**

The Spring Boot backend is responsible for:

- Authentication and authorization.
- Local-user setup and profile management.
- Focus-session lifecycle management.
- Active and paused time calculation.
- Pause finalization.
- Session completion, abandonment, interruption, and manual override handling.
- Streak configuration and period snapshots.
- Daily and weekly streak calculations.
- XP and gem transaction management.
- Streak-freeze consumption.
- Blocked-target and allowlist management.
- Extension session-state responses.
- Data export and deletion.

**Recommended package structure**

```
com.example.focusquest
  FocusQuestApplication.java

  config/
    CorsConfig.java
    SecurityConfig.java
    JwtConfig.java
    WebConfig.java

  security/
    JwtService.java
    JwtAuthenticationFilter.java
    CustomUserDetailsService.java
    AuthEntryPoint.java

  auth/
    AuthController.java
    AuthService.java
    LoginRequest.java
    LoginResponse.java
    SetupRequest.java
    SetupStatusResponse.java

  user/
    User.java
    UserRepository.java
    UserService.java
    UserController.java
    UserDto.java

  session/
    FocusSession.java
    SessionPause.java
    SessionStatus.java
    BlockingState.java
    TaskMode.java
    TaskCategory.java
    FocusSessionRepository.java
    SessionPauseRepository.java
    SessionService.java
    SessionController.java
    CreateSessionRequest.java
    FocusSessionDto.java

  streak/
    StreakConfiguration.java
    StreakPeriod.java
    StreakContribution.java
    StreakPeriodType.java
    StreakPeriodStatus.java
    StreakConfigurationRepository.java
    StreakPeriodRepository.java
    StreakContributionRepository.java
    StreakService.java
    StreakController.java
    StreakPeriodCalculator.java
    dto/

  blocking/
    RuleTarget.java                (shared base of the two rule entities)
    BlockedTarget.java
    AllowlistTarget.java
    TargetType.java
    RuleTargetRepository.java      (shared queries)
    BlockedTargetRepository.java
    AllowlistTargetRepository.java
    RuleNormalizer.java            (rule matching reference implementation, see section 9)
    UrlRule.java
    TargetUrl.java
    RulePrecedence.java
    BlockingService.java
    BlockingSnapshot.java
    CurrentSessionSnapshot.java
    BlockingController.java
    ExtensionController.java
    RuleTargetRequest.java, RuleTargetResponse.java
    BlockingStateResponse.java, ExtensionRule.java, CurrentSessionResponse.java
    HeartbeatRequest.java, HeartbeatResponse.java

  progression/
    ExperienceTransaction.java
    ExperienceTransactionType.java
    ExperienceTransactionRepository.java
    ExperienceService.java
    (planned, Phase 5: GemTransaction, GemTransactionType, StreakFreeze, ProgressionService,
     GemService, StreakFreezeService, ProgressionController)

  export/
    ExportService.java
    ExportController.java
    LocalDataExportDto.java

  shared/
    exception/
      GlobalExceptionHandler.java
      ErrorResponse.java
      ResourceNotFoundException.java
      InvalidSessionStateException.java
      InvalidRuleException.java
      DuplicateRuleException.java
      (ValidationException and UnauthorizedException are not needed so far: bean validation is
       reported by GlobalExceptionHandler and 401 by AuthEntryPoint)
    time/
      ClockProvider.java
      TimeCalculationService.java
    validation/
      UrlRuleValidator.java
```

Implementation notes on the structure above:

- Request and response DTOs live next to their controller rather than in `dto/` subpackages, and are mapped with a static `from(...)` factory instead of a `SessionMapper`.
- Not implemented yet: `UserController`, `StreakController` (and the streak DTOs), `WebConfig`, `TimeCalculationService`.

**Layering rules**

| Layer      | Responsibility                                                             |
| ---------- | -------------------------------------------------------------------------- |
| Controller | HTTP request/response handling, DTO validation, delegation to services     |
| ---        | ---                                                                        |
| Service    | Transactions, business rules, state transitions, calculation orchestration |
| ---        | ---                                                                        |
| Repository | JPA persistence and database queries                                       |
| ---        | ---                                                                        |
| Entity     | Database representation and domain state                                   |
| ---        | ---                                                                        |
| DTO        | API request and response representation                                    |
| ---        | ---                                                                        |
| Mapper     | Entity-to-DTO conversion; avoids exposing JPA entities directly            |
| ---        | ---                                                                        |
| Security   | JWT validation, authentication context, access control                     |
| ---        | ---                                                                        |

Controllers must not directly implement session-state rules, streak calculations, or XP rewards.

**Transaction boundaries**

The following operations should be transactional:

- Start session.
- Pause session.
- Resume session.
- Complete session.
- Abandon session.
- Manual override.
- Record streak contribution.
- Award XP.
- Apply XP penalty.
- Purchase or consume streak freeze.
- Complete or freeze a streak period.

Idempotency must be considered for requests that can produce rewards or contributions.

**Error responses**

Every error response is JSON of the form `{ "code": "...", "message": "..." }` (`ErrorResponse`).

| Code | HTTP status | Raised when |
| ---- | ----------- | ----------- |
| `INVALID_RULE` | 400 | A block or allowlist rule is not a valid domain or domain/path rule |
| `DUPLICATE_RULE` | 409 | A rule with the same normalized value already exists |
| `NOT_FOUND` | 404 | The resource does not exist or belongs to another user (deliberately indistinguishable) |
| `INVALID_SESSION_STATE` | 400 | An operation is not allowed from the session's current status |
| `VALIDATION_ERROR` | 400 | Request body fails bean validation; the message names each offending field |
| `UNAUTHORIZED` | 401 | Missing, invalid or expired bearer token |
| `CONFLICT`, `BAD_REQUEST`, `METHOD_NOT_ALLOWED`, ... | as named | Any other `ResponseStatusException` or Spring MVC error; the code is the HTTP status name |
| `INTERNAL_ERROR` | 500 | Anything unexpected. The message is always generic; the real exception is only logged |

`GlobalExceptionHandler` produces all of these except `UNAUTHORIZED`, which `AuthEntryPoint` writes in the security filter chain before Spring MVC is reached, using the same `ErrorResponse` shape. The typed exceptions extend `ResponseStatusException`, so services can keep throwing either.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**8\. Frontend architecture**

**Frontend responsibilities**

The Vue frontend is responsible for:

- Login and first-launch setup UI.
- Session creation.
- Task selection and category suggestion.
- Active-session timer display.
- Pause, resume, complete, abandon, and override controls.
- Streak configuration UI.
- Displaying streak progress.
- Displaying XP, gems, freezes, history, and settings.
- Calling backend APIs.
- Rendering understandable errors and confirmations.

The frontend must not be the source of truth for session completion, XP, streak progress, or blocking enforcement.

**Recommended frontend structure**

```
src/
  api/
    authApi.ts
    sessionApi.ts
    streakApi.ts
    blockingApi.ts
    progressionApi.ts
    settingsApi.ts
    extensionApi.ts

  components/
    common/
      AppButton.vue
      ConfirmDialog.vue
      ErrorMessage.vue
      LoadingIndicator.vue
      Modal.vue

    session/
      SessionForm.vue
      TaskTypeSelector.vue
      CategorySelector.vue
      BlockedTargetSelector.vue
      ActiveSessionView.vue
      PausedSessionView.vue
      SessionTimer.vue
      SessionSummary.vue
      ManualOverrideDialog.vue

    streak/
      StreakConfigurationForm.vue
      StreakProgress.vue
      StreakHistory.vue

    blocking/
      BlockRuleForm.vue
      AllowlistRuleForm.vue
      RuleList.vue

    progression/
      XpDisplay.vue
      GemDisplay.vue
      FreezeInventory.vue
      XpHistory.vue

  views/
    LoginView.vue
    SetupView.vue
    DashboardView.vue
    SessionCreateView.vue
    HistoryView.vue
    StreaksView.vue
    BlockingRulesView.vue
    SettingsView.vue

  router/
    index.ts
    navigationGuard.ts

  stores/
    authStore.ts
    sessionStore.ts
    streakStore.ts
    progressionStore.ts
    settingsStore.ts

  types/
    auth.ts
    session.ts
    streak.ts
    blocking.ts
    progression.ts
    api.ts

  utils/
    duration.ts
    dateTime.ts
    validation.ts

  App.vue
  main.ts
```

**State management**

Use Pinia for cross-view state such as:

- Authentication state.
- Current focus session.
- Current streak period and progress.
- XP and gem balances.
- Local profile settings.

Keep API calls inside api/ modules or stores rather than putting fetch logic directly into presentational components.

**Authentication flow in the frontend**

- `api/client.ts` is a single Axios instance. It adds `Authorization: Bearer <token>` to every request and reports 401 responses to the auth store. It receives the store through `configureApiClient()` in `main.ts` rather than importing it, so the api layer does not depend on the stores. The backend URL comes from `VITE_API_BASE_URL` and defaults to `http://127.0.0.1:8080`.
- `stores/authStore.ts` owns the token, its expiry and the signed-in user, and exposes `login`, `setup`, `restoreSession`, `fetchSetupRequired` and `logout`.
- `router/navigationGuard.ts` runs before every navigation. Routes are protected unless they carry `meta.public`.
  - A protected route without a valid session redirects to login, keeping the requested path in a `redirect` query parameter. Only same-app paths are honoured as redirect targets.
  - A signed-in user who opens login or setup is sent to the dashboard.
  - A signed-out user who opens login or setup is routed by `GET /api/auth/setup-status`: to setup while `setupRequired` is true, to login once it is false. If the backend cannot be reached the requested screen is shown and reports the connection error.
- The dev server runs on port 5173 (`strictPort`), the only frontend origin the backend's CORS configuration allows.

**Session management in the frontend**

- `api/sessionApi.ts` wraps every `/api/focus-sessions` endpoint the UI uses; `stores/sessionStore.ts` holds the current session, the last ended session and the history.
- Every state change (create, start, pause, resume, complete, abandon, override) is a backend call, and the returned `FocusSessionDto` replaces the local copy. The UI never computes session state, completion eligibility, XP or streak progress. When a call fails, the store re-fetches the current session so the screen reflects what the server actually holds, and the backend's error message is shown.
- Creating a session only creates it as `PLANNED`; the user starts it with a separate action, which is when the timer and blocking begin. The backend cannot list `PLANNED` sessions, so the store remembers the planned one locally and it is lost on a page reload. A `PLANNED` session that is never started, or is replaced by editing the details, stays on the server and blocks nothing.
- `SessionTimer` counts locally between server responses, from the moment each response arrived (`Date.now()` at receipt) rather than from the server's `generatedAt`, so browser clock skew cannot distort it. When the countdown reaches zero the dashboard re-fetches the session, and the Complete button is enabled from the server's `remainingFocusSeconds`, not the local countdown. The dashboard also re-fetches when the tab becomes visible again.
- Abandon asks for confirmation. Manual override is offered only after a session has been abandoned with blocking still held: the session summary on the dashboard shows an "Override blocking" button, and the running and paused views have none. It opens `ManualOverrideDialog`, which warns that an XP penalty will be applied without stating an amount (the penalty is a server-side, configurable placeholder), and calls the override endpoint only after the user confirms. Because the abandoned session is otherwise lost on a reload, the store looks at the latest history entry when there is no running session and restores it if it is `ABANDONED` with blocking `ACTIVE`; that summary has no dismiss button while the override is available.
- The session views are `DashboardView` (current session, or the summary of the one that just ended), `SessionCreateView` and `HistoryView`.

**Frontend security behavior**

- Send bearer tokens in authenticated API requests.
- Redirect to login on an HTTP 401 response, but only when the rejected request carried a bearer token (an expired or invalid session). A 401 on `POST /api/auth/login` means wrong credentials and is shown on the login form instead.
- Do not trust UI-only validation for business rules.
- Do not store secrets in source code.
- Do not expose extension credentials in website content scripts.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**9\. Chrome extension architecture**

**Extension responsibilities**

The Chrome extension is responsible for:

- Receiving the current blocking configuration from the backend.
- Determining whether a requested URL should be blocked.
- Applying domain and URL-path block rules.
- Applying allowlist precedence.
- Displaying an extension-hosted blocked page.
- Restoring enforcement after an extension or browser restart.
- Communicating authentication state and backend availability safely.

The extension must not decide whether a session is completed, whether XP is awarded, or whether a streak has been reached. Those decisions belong to the backend.

**Recommended extension structure**

```
extension/
  manifest.json

  src/
    background/
      serviceWorker.ts
      backendClient.ts
      sessionStateSynchronizer.ts
      dynamicRulesManager.ts
      blockingStateStore.ts

    blocking/
      urlMatcher.ts
      rulePrecedence.ts
      ruleNormalizer.ts

    pages/
      blocked/
        blocked.html
        blocked.ts
        blocked.css

    types/
      api.ts
      blocking.ts
      session.ts

    utils/
      chromeStorage.ts
      logger.ts

  assets/
    icons/
```

**Manifest V3 components**

- **Service worker:** Background synchronization, API communication, dynamic rule updates.
- **Declarative Net Request rules:** Prefer this for reliable URL blocking where it fits the required matching behavior.
- **Blocked page:** A local extension page displayed for matching blocked URLs.
- **Chrome storage:** Persist extension state and token across service-worker lifecycle changes.

**Rule behavior**

**Domain rule**

A domain block should apply to the domain and relevant subdomains.

Example:

```
youtube.com
```

Should include:

```
youtube.com
www.youtube.com
m.youtube.com
```

**Path rule**

A path block should apply to the given path and all descendant paths.

Example:

```
youtube.com/shorts
```

Should include:

```
youtube.com/shorts
youtube.com/shorts/example
youtube.com/shorts?feature=share
```

**Allowlist precedence**

Recommended matching precedence:

1. Most-specific allowlist rule.
2. Most-specific block rule.
3. Broader allowlist rule.
4. Broader block rule.
5. Default allow.

This rule must be expressed in the extension implementation and covered by tests.

**Rule normalization, matching and precedence (reference implementation)**

The backend contains a reference implementation of the rules in the `blocking` package (`RuleNormalizer`, `UrlRule`, `TargetUrl`, `RulePrecedence`) with its own tests. The extension's `ruleNormalizer.ts`, `urlMatcher.ts` and `rulePrecedence.ts` must reproduce its behavior exactly.

- **Rule syntax:** a domain (`example.com`) or a domain plus path (`example.com/docs`). No scheme, port, query string, fragment, wildcard, or `.`/`..` path segment. A domain needs at least two labels. Query strings are rejected rather than stripped, because dropping one silently would widen the rule.
- **Normalization:** the rule is lowercased and a trailing slash is dropped. A leading `www.` is kept, since `www.example.com` and `example.com` are different rules with different specificity. Stored values, uniqueness checks and the rules sent to the extension are all in this canonical form.
- **URL side:** only `http` and `https` URLs are evaluated. The host is lowercased and loses a trailing dot; the path is percent-decoded, lowercased, has dot segments and repeated slashes collapsed, and ignores a trailing slash; the query string and fragment are ignored. Matching is deliberately forgiving so a URL cannot dodge a rule by being spelled differently (for example `/%73horts` or `//shorts`), and path matching is case-insensitive, as Chrome's own declarative rules are.
- **Matching:** the host matches if it equals the rule host or is a subdomain of it (`youtube.com` matches `m.youtube.com`, not `notyoutube.com`). A path rule matches the path itself and its descendants on a segment boundary (`/shorts` matches `/shorts/abc`, not `/shortsfoo`). A domain rule matches every path.
- **Specificity:** a rule with more host labels is more specific; if equal, the one with more path segments is. Host specificity outranks path specificity, so `m.youtube.com` (allowed) beats `youtube.com/shorts` (blocked) for `m.youtube.com/shorts`.
- **Precedence:** among all rules that match a URL, the most specific one wins; when an allowlist rule and a block rule are equally specific, the allowlist rule wins. This yields the order above: most-specific allowlist, most-specific block, broader allowlist, broader block, default allow. Only active rules take part.

**Extension synchronization**

The extension should synchronize with the backend:

- When the extension starts.
- When Chrome restarts.
- When the extension service worker wakes.
- When the user starts, pauses, resumes, completes, abandons, or overrides a session.
- Periodically while enforcement is expected to be active, if needed.

If a session is active or paused, the extension should restore blocking after restart.

**Extension behavior when frontend is closed**

If the Vue frontend is closed but the backend remains available:

- The extension should continue blocking an active or paused session.
- The extension should use backend state to determine whether blocking remains required.
- The user should be able to reopen the frontend and continue managing the session.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**10\. API architecture**

All APIs use JSON. Authenticated endpoints require:

```
Authorization: Bearer <JWT>
```

**Authentication**

```
GET  /api/auth/setup-status
POST /api/auth/setup
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/change-password
```

- **`setup-status`** is public and returns `{ "setupRequired": true }` while no local user exists and `{ "setupRequired": false }` afterwards. The frontend uses it to choose between the setup and login screens. It exposes nothing beyond what `setup` already reveals: that call returns `409 CONFLICT` once an account exists.
- **`setup`** and **`login`** both return `token`, `tokenType`, `expiresInSeconds` and the `user`. `setup` creates the single local account (`username` 3-100 characters, `password` 8-100, `displayName` up to 100, `timezone` an IANA zone id up to 50), creates its default streak configuration, and responds `201 CREATED`.
- **`login`** answers a wrong username or password with `401 UNAUTHORIZED`, the same status as an expired session. Clients tell the two apart by whether the request carried a bearer token.

**Session lifecycle**

```
POST /api/focus-sessions
GET  /api/focus-sessions/current
GET  /api/focus-sessions/{id}
GET  /api/focus-sessions/history

POST /api/focus-sessions/{id}/start
POST /api/focus-sessions/{id}/pause
POST /api/focus-sessions/{id}/resume
POST /api/focus-sessions/{id}/complete
POST /api/focus-sessions/{id}/abandon
POST /api/focus-sessions/{id}/override
```

Notes:

- `POST /api/focus-sessions` returns 201 with the planned session. The session body is `FocusSessionDto`; `activeFocusSeconds` and `remainingFocusSeconds` are live (they include the segment still running as of `generatedAt`).
- `GET /api/focus-sessions/current` returns the ACTIVE or PAUSED session, or 204 No Content.
- `GET /api/focus-sessions/history` returns the caller's ended sessions (`COMPLETED`, `ABANDONED`, `INTERRUPTED`) as a JSON array of `FocusSessionDto`, most recently started first. The optional `limit` parameter defaults to 50 and is clamped to 1-200. There is no paging yet.
- `GET /api/focus-sessions/{id}` is not implemented yet.
- `POST .../override` takes no body and no override reason is collected yet. It is accepted only for an `ABANDONED` session whose blocking is still `ACTIVE`, that is still the user's latest started session, and while today's daily target is unmet. Calling it on an `ACTIVE` or `PAUSED` session is refused with `INVALID_SESSION_STATE` ("Abandon the session before overriding website blocking"), so a session is always ended and its time credited before anything is overridden.
- Every `{id}` operation checks that the session belongs to the caller; another user's session is reported as `NOT_FOUND`.

**Streaks**

```
GET  /api/streaks/current
GET  /api/streaks/history
GET  /api/streak-configurations
POST /api/streak-configurations
PUT  /api/streak-configurations/{id}
GET  /api/streak-periods/current
```

None of the streak endpoints are implemented yet. Until they are, every user simply has the default daily configuration described in section 11.

**Blocking and allowlists**

```
GET    /api/blocked-targets
POST   /api/blocked-targets
PUT    /api/blocked-targets/{id}
DELETE /api/blocked-targets/{id}

GET    /api/allowlist-targets
POST   /api/allowlist-targets
PUT    /api/allowlist-targets/{id}
DELETE /api/allowlist-targets/{id}
```

Notes:

- Request body: `{ "targetValue": "example.com/docs", "displayName": "Docs", "active": true }`. Only `targetValue` is required. `displayName` defaults to the normalized rule; `active` defaults to true on create and to the current value on update.
- The rule type (`DOMAIN` or `URL_PATH`) is derived from `targetValue` and returned in the response; clients never send it.
- Creating returns 201; deleting returns 204. A rule that duplicates an existing one after normalization is rejected with `DUPLICATE_RULE`.
- **Configuration lock:** while blocking is being enforced (see section 13), the configuration may only get stricter. Creating a block rule and deleting an allowlist rule are allowed; editing or deleting a block rule, and creating or editing an allowlist rule, are refused with 409.

**Progression**

```
GET  /api/me/progression
GET  /api/me/xp-history
GET  /api/me/gems
GET  /api/me/gem-history
GET  /api/me/streak-freezes
POST /api/me/streak-freezes/purchase
```

**Extension-specific API**

The extension uses three endpoints, authenticated with the same bearer token as the rest of the API:

```
GET  /api/extension/blocking-state
GET  /api/extension/current-session
POST /api/extension/heartbeat
```

- **`blocking-state`** returns `enforcementActive`, `sessionId`, `blockingState`, `stateVersion`, `generatedAt`, and the active `blockRules` and `allowRules`. Each rule carries its `targetType`, canonical `targetValue`, pre-split `host` and `path` (null for a domain rule), and `displayName`. When enforcement is not active, both rule lists are empty and the extension should remove any blocking it has installed.
- **`current-session`** returns what the blocked page shows: session id, status, blocking state, task description, planned/active/remaining seconds, start time, and today's daily streak progress (`qualifyingSeconds`, `targetSeconds`, `status`). It returns 204 No Content when nothing is being enforced. `dailyStreak` is null until time has first been credited that day.
- **`heartbeat`** accepts an optional `{ "stateVersion": "..." }` and returns `serverTime`, `enforcementActive`, `sessionId`, the current `stateVersion` and `refreshRequired`. The extension calls it periodically and re-fetches `blocking-state` only when `refreshRequired` is true.
- `stateVersion` is a fingerprint of the enforcement flag, the enforcing session and its blocking state, and the active rules. It changes exactly when the extension must re-synchronize, and is not affected by a pause or resume, which do not change blocking.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**11\. Database architecture**

**Database choice**

The MVP uses a persistent local H2 database. H2 is sufficient for:

- One local user.
- Local development.
- Session, streak, and transaction persistence.
- Demonstration purposes.

PostgreSQL becomes preferable when adding:

- Multiple users.
- Remote deployment.
- Synchronization across devices.
- More concurrent access.
- Advanced reporting or analytics.

**Storage conventions**

- Store timestamps as UTC instants or offset-aware timestamps.
- Use the user's configured time zone to calculate daily and weekly boundaries.
- Store durations in seconds internally.
- Display durations in minutes in the UI.
- Use enumerations for state and type values.
- Use database constraints and application validation for critical invariants.

**Main entities**

**User**

```
User
- id
- username
- passwordHash
- displayName
- timezone
- createdAt
```

**FocusSession**

```
FocusSession
- id
- userId
- taskDescription
- taskMode
- taskCategory
- plannedFocusMinutes
- activeFocusSeconds
- finalizedPausedSeconds
- qualifyingSeconds
- overtimeSeconds
- status
- blockingState
- startedAt
- completedAt
- abandonedAt
- overrideUsed
- completionXpAwarded
- createdAt
- activeSegmentStartedAt
- streakCreditedActiveSeconds
- streakCreditedPausedSeconds
```

The last three are internal bookkeeping. `activeSegmentStartedAt` marks when the current uninterrupted ACTIVE stretch began, so elapsed time can be added across pause/resume cycles. The two `streakCredited...` fields record how much of the session's time has already been credited to the streak, so each credit covers only the time since the previous one (see section 13).

**SessionPause**

```
SessionPause
- id
- sessionId
- startedAt
- endedAt
- durationSeconds
- finalized
```

**BlockedTarget**

```
BlockedTarget
- id
- userId
- targetType
- targetValue
- displayName
- active
```

**AllowlistTarget**

```
AllowlistTarget
- id
- userId
- targetType
- targetValue
- displayName
- active
```

**StreakConfiguration**

```
StreakConfiguration
- id
- userId
- periodType
- targetMinutes
- requiredTaskMode
- requiredCategory
- effectiveFrom
- createdAt
```

**Default configuration.** A user always has a daily streak configuration, so there is never "no streak". The default is `DAILY`, `targetMinutes = 30`, `requiredTaskMode = TASK_REQUIRED`, no required category (task-free sessions therefore do not count until the user changes it), effective from the epoch. It is created when the account is set up, and `StreakService` creates it on first use for any user that lacks one, so accounts that pre-date the default are covered too. Only the daily period has a default: a weekly streak exists only if the user configures one.

**StreakPeriod**

```
StreakPeriod
- id
- userId
- configurationSnapshotId
- periodType
- startTime
- endTime
- targetMinutes
- requiredTaskMode
- requiredCategory
- qualifyingSeconds
- overtimeSeconds
- status
- freezeConsumed
- completedAt
```

`qualifyingSeconds` and `overtimeSeconds` are tracked in seconds, not minutes, so that repeated small contributions (short sessions, finalized pauses) accumulate exactly instead of losing time to per-contribution minute rounding. `targetMinutes` remains a minute-granularity configuration value.

**StreakContribution**

```
StreakContribution
- id
- streakPeriodId
- sessionId
- activeSeconds
- pausedSeconds
- qualifyingSeconds
- createdAt
```

A session normally has several contribution rows per period: one each time a pause is resumed, and one when the session ends. Each row holds only the time since the previous credit.

**ExperienceTransaction**

```
ExperienceTransaction
- id
- userId
- amount
- type
- referenceType
- referenceId
- createdAt
```

Implemented so far: the only `type` is `MANUAL_OVERRIDE_PENALTY`, with `referenceType = FOCUS_SESSION` and a negative `amount`. A unique index on `(userId, type, referenceType, referenceId)` guarantees a session is penalized at most once.

**GemTransaction**

```
GemTransaction
- id
- userId
- amount
- transactionType
- referenceId
- createdAt
```

**StreakFreeze**

```
StreakFreeze
- id
- userId
- status
- purchasedAt
- consumedAt
- consumedForPeriodId
```

**BlockingOverride**

```
BlockingOverride
- id
- sessionId
- reason
- xpPenalty
- createdAt
```

Not implemented yet. For now an override is recorded by `FocusSession.overrideUsed`, the `OVERRIDE_USED` blocking state, and the penalty `ExperienceTransaction`.

**Important constraints and indexes**

Recommended constraints:

- Unique user username.
- One active or paused focus session per user.
- A completed session cannot transition to another state.
- One completion-XP transaction per focus session.
- One contribution record per session, streak period, and finalized interval where required.
- Non-negative duration fields.
- plannedFocusMinutes >= 5.
- Non-negative gem balance after a purchase.

Recommended indexes:

```
focus_sessions(user_id, status)
focus_sessions(user_id, started_at)
session_pauses(session_id)
streak_periods(user_id, period_type, start_time)
streak_contributions(streak_period_id)
experience_transactions(user_id, created_at)
gem_transactions(user_id, created_at)
blocked_targets(user_id, active)
allowlist_targets(user_id, active)
```

Additional unique constraints: `blocked_targets(user_id, target_value)`, `allowlist_targets(user_id, target_value)`, and `experience_transactions(user_id, type, reference_type, reference_id)`. `BlockedTarget` and `AllowlistTarget` share their fields; `targetValue` is always the canonical normalized rule, `targetType` is derived from it, and `displayName` defaults to it.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**12\. Core domain services**

**SessionService**

Responsibilities:

- Create a planned session.
- Start a session.
- Pause a session.
- Resume a session.
- Complete a session.
- Abandon a session.
- Handle interruption and recovery.
- Finalize active and pause intervals.
- Enforce valid state transitions and session ownership.
- Credit streak progress when a pause is resumed and when a session ends.
- Settle the session's blocking state when it ends.
- Apply the manual override to an already-abandoned session (set `overrideUsed`, release blocking, record the penalty).

**StreakService**

Responsibilities:

- Guarantee the default daily configuration exists.
- Create or load current daily or weekly period.
- Apply configuration snapshots.
- Determine whether a session qualifies.
- Record qualifying time.
- Track progress and overtime.
- Complete a streak period.
- Mark a missed period.
- Consume a streak freeze automatically where appropriate.

**BlockingService**

Responsibilities:

- Store block and allowlist rules: create, update, delete and list, with normalization, duplicate detection and per-user ownership.
- Refuse rule changes that would loosen blocking while enforcement is active.
- Determine whether enforcement is active, derived from the latest started session's status (section 13).
- Build the state the extension synchronizes: rules, `stateVersion`, and the blocked-page session details.
- Apply the matching and precedence rules to a URL (`evaluateUrl`), for tests and later UI use.

**ExperienceService**

Responsibilities:

- Award completion XP exactly once (planned).
- Apply manual-override penalties (implemented; once per session, amount from `focusquest.xp.manual-override-penalty`, currently a placeholder of 10).
- Calculate daily XP according to the configured rules.
- Keep XP transaction history.

**GemService**

Responsibilities:

- Calculate gem rewards from XP when rules are finalized.
- Record gem transactions.
- Validate purchases.

**StreakFreezeService**

Responsibilities:

- Purchase freezes with gems.
- Find available freezes.
- Consume a freeze when a streak period is missed.
- Record the relationship between a freeze and the protected streak period.

**ExportService**

Responsibilities:

- Export all local data as JSON.
- Support a full local backup.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**13\. Time and state rules**

**Session state machine**

```
PLANNED -> ACTIVE
ACTIVE -> PAUSED
PAUSED -> ACTIVE
ACTIVE -> COMPLETED
ACTIVE -> ABANDONED
PAUSED -> ABANDONED
ACTIVE -> INTERRUPTED
PAUSED -> INTERRUPTED
```

COMPLETED, ABANDONED, and INTERRUPTED are terminal session states for the MVP unless a future recovery design explicitly changes this.

**Pause rule**

- Starting a pause records startedAt.
- An unresolved pause contributes nothing to the streak yet.
- Resuming, abandoning, interrupting, or finalizing closes the pause.
- A finalized pause contributes to qualifying time if the session matches the streak configuration.
- The pause is finalized, and credited, when the session is resumed, abandoned or overridden (an interrupted session discards it).
- Paused time never counts toward the required active focus duration for session completion.
- Website blocking remains active throughout the pause.

**Streak crediting rule**

Streak progress is credited at two moments:

1. **On resume:** the total time already passed in the session (the active time before the pause plus the pause just finalized) is credited to the current daily and weekly periods at once, so the day's total is up to date while the session continues.
2. **When the session ends** (complete, abandon, override): only the time since the last credit is credited.

The session records what it has already credited, so no time is counted twice. An unresolved pause contributes nothing. Time in an interrupted session that was not yet credited is discarded, but anything credited at an earlier resume stays. Each credit goes to the period current at the moment it is made.

**Session completion rule**

A session can be completed when:

```
activeFocusTime >= plannedFocusTime
```

Completion grants session XP once. The session becomes immutable.

**Abandonment rule**

An abandoned session:

- Grants no session-completion XP.
- Keeps recorded active and finalized paused time for streak progress if the session qualifies.
- Releases websites immediately only when the daily target has already been reached, counting the time credited by this abandonment.
- Otherwise maintains enforcement until a new session is completed, or a manual override of that abandoned session. Starting a new session supersedes the abandoned one.

**Manual override rule**

A manual override:

- Releases website blocking immediately, with blocking state `OVERRIDE_USED`.
- Is allowed only for a session that is already ABANDONED and still holding blocking (blocking state `ACTIVE`), that is the user's latest started session, while today's daily target is unmet. It is refused for ACTIVE, PAUSED, PLANNED and COMPLETED sessions and cannot be applied twice.
- Sets overrideUsed = true on the abandoned session. The session's time was already credited to the streak when it was abandoned, so an override credits nothing further.
- Applies an XP penalty as a negative `ExperienceTransaction`, once per session. The amount is a placeholder (10) set in `focusquest.xp.manual-override-penalty`.
- Stores the audit trail as `overrideUsed`, the blocking state and the transaction (a dedicated `BlockingOverride` record is planned).

**Enforcement and release rule**

Whether the extension must block is derived from the status of the user's most recently started session, not from the stored blocking state alone:

| Latest session | Enforcing? |
| -------------- | ---------- |
| ACTIVE or PAUSED | Yes |
| COMPLETED | No; completing sets `RELEASED` |
| INTERRUPTED | No; sets `TECHNICAL_RELEASE`, so a technical failure never locks the user out |
| ABANDONED | Yes, unless the blocking state is no longer `ACTIVE` (released or overridden) or today's daily target has been reached |
| PLANNED / none | No |

When a session is abandoned, the blocking state is set to `RELEASED` if the daily target is reached afterwards, otherwise it stays `ACTIVE`. The backend keeps one blocking configuration per user (the active block and allowlist rules) rather than a per-session snapshot; the configuration lock in section 10 stops it being loosened during enforcement.

**Daily and weekly boundaries**

- Daily periods use the local calendar day in the user's configured time zone.
- Weekly periods run from Monday at 00:00 through Sunday at 23:59:59 in the user's configured time zone.
- Timestamp calculations should use a configurable Clock abstraction to make tests deterministic.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**14\. Testing architecture**

**Backend tests**

Use:

- JUnit 5.
- Mockito.
- Spring Boot integration tests.
- H2 for simple integration tests.
- A full session-lifecycle integration test with no mocks (real services and an in-memory database), covering streak crediting, release decisions, override and rule locking.
- Controller slice tests that load the real security configuration (`@WithRealSecurityConfig`); without it a `@WebMvcTest` silently runs under Spring Boot's default security.
- Testcontainers with PostgreSQL before migration or production deployment.

High-priority unit tests:

- Valid and invalid session-state transitions.
- Minimum session duration.
- Active-time calculation.
- Pause finalization.
- Resume behavior.
- Completion conditions.
- Abandonment behavior.
- Manual override penalty.
- Qualifying-time calculation.
- Daily boundary behavior.
- Monday-to-Sunday weekly behavior.
- Configuration snapshots.
- Overtime behavior.
- Streak-freeze consumption.
- Duplicate XP prevention.
- Duplicate contribution prevention.
- URL-rule validation.

**Frontend tests**

Use Vitest for:

- Form validation.
- Task-type/category suggestion.
- Session-control visibility by session state.
- Streak-progress rendering.
- Override confirmation dialog.
- Error states.

**Extension tests**

Test:

- Domain matching.
- Subdomain matching.
- Path matching.
- Child-path matching.
- Query-parameter behavior.
- Allowlist precedence.
- Block precedence.
- Rule normalization.
- Restoring state after extension restart.

**End-to-end tests**

Later use Playwright to test:

1. Login.
2. Create a session.
3. Start the session.
4. Confirm extension enforcement.
5. Pause and resume.
6. Complete or abandon.
7. Verify streak progress.
8. Verify XP and penalty handling.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**15\. Development phases**

**Phase 1: Foundation**

- Create Git repository.
- Create Spring Boot project.
- Create Vue 3 + TypeScript project.
- Create Chrome extension project.
- Add Flyway migrations.
- Configure H2 persistence.
- Add Spring Security and JWT authentication.
- Implement first-launch user setup and login.

**Phase 2: Focus-session domain**

- Implement user entity and repository.
- Implement focus sessions and state transitions.
- Implement pause intervals.
- Implement time calculations.
- Add service-level tests.
- Build basic session creation and active-session UI.

**Phase 3: Streaks**

- Implement streak configuration.
- Implement daily and weekly period snapshots.
- Implement qualifying-time contributions.
- Implement overtime.
- Implement streak completion and missed periods.

**Phase 4: Blocking extension**

- Implement block and allowlist rule storage.
- Implement extension authentication.
- Implement extension state synchronization.
- Implement domain and path matching.
- Implement blocked page.
- Implement restart restoration.

**Phase 5: Progression**

- Implement XP transaction model.
- Award XP for completed sessions.
- Implement override penalties.
- Implement XP display.
- Define gem economy.
- Implement gems and streak freezes.

**Phase 6: Hardening**

- Add export and deletion.
- Improve error handling.
- Add backup support.
- Add end-to-end tests.
- Verify security configuration.
- Test restart, interrupted sessions, and extension recovery.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**16\. Future scalability and migration notes**

The MVP is intentionally local and single-user. Future migration should preserve the modular service boundaries.

**Future changes**

| Future requirement   | Architectural change                                                                    |
| -------------------- | --------------------------------------------------------------------------------------- |
| Multiple users       | Add roles, multi-user authorization, remote database, stronger account management       |
| ---                  | ---                                                                                     |
| Deployment           | Replace H2 with PostgreSQL, deploy backend and frontend, configure HTTPS                |
| ---                  | ---                                                                                     |
| Cross-device sync    | Add account synchronization and conflict handling                                       |
| ---                  | ---                                                                                     |
| Desktop app blocking | Add Electron or Tauri client and platform-specific process management                   |
| ---                  | ---                                                                                     |
| More browsers        | Build browser-specific extension implementations or standards-compliant extension layer |
| ---                  | ---                                                                                     |
| Computer vision      | Add a separate local Python service; exchange only aggregate events                     |
| ---                  | ---                                                                                     |
| Social features      | Add profile visibility, groups, moderation, notifications, and privacy controls         |
| ---                  | ---                                                                                     |

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**17\. Technical decisions summary**

| Area               | Decision                                                                            |
| ------------------ | ----------------------------------------------------------------------------------- |
| Architecture style | Modular monolith                                                                    |
| ---                | ---                                                                                 |
| Frontend           | Vue 3 + TypeScript + Vite                                                           |
| ---                | ---                                                                                 |
| Backend            | Java 21 + Spring Boot 4                                                             |
| ---                | ---                                                                                 |
| Security           | Spring Security + BCrypt + JWT bearer tokens                                        |
| ---                | ---                                                                                 |
| Database           | H2 initially; PostgreSQL later                                                      |
| ---                | ---                                                                                 |
| Migrations         | Flyway recommended                                                                  |
| ---                | ---                                                                                 |
| Browser support    | Chrome first                                                                        |
| ---                | ---                                                                                 |
| Extension standard | Chrome Manifest V3                                                                  |
| ---                | ---                                                                                 |
| Extension language | TypeScript                                                                          |
| ---                | ---                                                                                 |
| User model         | One local authenticated user for the MVP                                            |
| ---                | ---                                                                                 |
| Persistence        | Local database                                                                      |
| ---                | ---                                                                                 |
| Session authority  | Spring Boot backend                                                                 |
| ---                | ---                                                                                 |
| Blocking authority | Chrome extension using backend state                                                |
| ---                | ---                                                                                 |
| Time storage       | Seconds internally, minutes in UI                                                   |
| ---                | ---                                                                                 |
| Daily boundary     | User's local calendar day                                                           |
| ---                | ---                                                                                 |
| Weekly boundary    | Monday through Sunday, user's local time zone                                       |
| ---                | ---                                                                                 |
| Testing            | JUnit, Mockito, Spring integration tests, Vitest, extension tests, Playwright later |
| ---                | ---                                                                                 |

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**18\. Immediate next implementation step**

Begin with the secure application foundation:

1. Initialize the Spring Boot project with Web, Data JPA, Validation, Security, H2, Flyway, and test dependencies.
2. Create the Vue 3 + TypeScript project.
3. Create the Chrome Extension Manifest V3 project skeleton.
4. Implement the User entity, first-launch setup, BCrypt password hashing, JWT generation, JWT validation filter, login endpoint, and protected test endpoint.
5. Implement the frontend setup and login views.
6. Verify that both the frontend and extension can call a protected backend endpoint with a valid bearer token.

After security works end to end, implement the focus-session domain and its unit tests before connecting detailed UI behavior.