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
- Spring Boot 3.
- Spring Web.
- Spring Data JPA.
- Spring Validation.
- Spring Security.
- H2 database for local development and MVP persistence.
- Flyway or Liquibase for database migrations; Flyway is recommended.
- Maven.
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
2. User enters local username and password.
3. Frontend sends credentials to /api/auth/login.
4. Backend validates password using BCrypt.
5. Backend creates a signed JWT.
6. Frontend stores the token securely for the local application session.
7. Frontend sends Authorization: Bearer <token> with API requests.
8. Chrome extension stores and uses the same token for extension API calls.
```

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

<div class="joplin-table-wrapper"><table><thead><tr><th><p>Endpoint group</p></th><th><p>Access policy</p></th></tr><tr><th><pre><code>/api/auth/login</code></pre></th><th><p>Public</p></th></tr><tr><th><pre><code>/api/auth/setup</code></pre></th><th><p>Public only when no local user exists</p></th></tr><tr><th><p>/api/auth/** remaining protected operations</p></th><th><p>Authenticated where applicable</p></th></tr><tr><th><pre><code>/api/**</code></pre></th><th><p>Authenticated</p></th></tr><tr><th><p>Extension endpoints</p></th><th><p>Authenticated with bearer token</p></th></tr><tr><th><p>Health endpoint, if exposed</p></th><th><p>Local-only or public depending on configuration</p></th></tr></thead></table></div>

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
    SessionMapper.java
    dto/

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
    BlockedTarget.java
    AllowlistTarget.java
    TargetType.java
    BlockedTargetRepository.java
    AllowlistTargetRepository.java
    BlockingService.java
    BlockingController.java
    ExtensionController.java
    dto/

  progression/
    ExperienceTransaction.java
    ExperienceTransactionType.java
    GemTransaction.java
    GemTransactionType.java
    StreakFreeze.java
    ProgressionService.java
    ExperienceService.java
    GemService.java
    StreakFreezeService.java
    ProgressionController.java
    dto/

  export/
    ExportService.java
    ExportController.java
    LocalDataExportDto.java

  shared/
    exception/
      GlobalExceptionHandler.java
      ResourceNotFoundException.java
      InvalidSessionStateException.java
      ValidationException.java
      UnauthorizedException.java
    time/
      ClockProvider.java
      TimeCalculationService.java
    validation/
      UrlRuleValidator.java
```

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

**Frontend security behavior**

- Send bearer tokens in authenticated API requests.
- Redirect to login on an HTTP 401 response.
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
POST /api/auth/setup
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/change-password
```

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

**Streaks**

```
GET  /api/streaks/current
GET  /api/streaks/history
GET  /api/streak-configurations
POST /api/streak-configurations
PUT  /api/streak-configurations/{id}
GET  /api/streak-periods/current
```

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

The extension can use authenticated endpoints such as:

```
GET /api/extension/blocking-state
GET /api/extension/current-session
POST /api/extension/heartbeat
```

The exact endpoints may be consolidated. The extension should receive only the data it needs:

- Whether enforcement is active.
- Blocking state.
- Session identifier.
- Block rules.
- Allowlist rules.
- Relevant session display information for the blocked page.

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
```

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
- Enforce valid state transitions.

**StreakService**

Responsibilities:

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

- Store block and allowlist rules.
- Build the rule configuration required by the extension.
- Determine whether enforcement should be active.
- Determine release behavior after completion, abandonment, or override.

**ExperienceService**

Responsibilities:

- Award completion XP exactly once.
- Apply manual-override penalties.
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
- Paused time never counts toward the required active focus duration for session completion.
- Website blocking remains active throughout the pause.

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
- Releases websites immediately only when the daily target has already been reached.
- Otherwise maintains enforcement until a new qualifying session, another applicable release condition, or a manual override.

**Manual override rule**

A manual override:

- Releases website blocking immediately.
- Marks the session as abandoned with overrideUsed = true.
- Applies a daily XP penalty.
- Stores an audit record.

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
| Backend            | Java 21 + Spring Boot 3                                                             |
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