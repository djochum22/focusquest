**FocusQuest**

**Technical Architecture Specification**

**Document status:** Working draft

**Project type:** Local-first productivity web application with a Chrome browser extension

**MVP:** One local user, local data storage, secure backend API, Chrome website blocking, focus sessions, streaks, XP, levels, and gems, and streak freezes bought with gems. A missed week, or a missed day no freeze covers, ends the streak.

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**1\. Purpose**

This document defines the technical architecture for FocusQuest. It describes the application components, responsibilities, communication paths, security design, database model, API direction, project structure, and testing approach.

FocusQuest is a local-first productivity application. The user creates focus sessions, chooses websites or URL paths to block, tracks qualifying work toward a daily or weekly streak, and receives XP for completing sustained focus sessions.

The system separates:

- **Streak progress:** Accumulated qualifying time across sessions.
- **Session completion:** Completion of a planned amount of active focus time.
- **Browser enforcement:** Blocking selected websites while enforcement is active.
- **Rewards:** XP, levels and gems (section 19). Gems buy streak freezes, which bridge missed days of the daily streak.

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
| H2 database         | Local storage for user, sessions, pauses, streaks, block rules, XP, gems, configuration                               |
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
- Playwright for the end-to-end tests (`e2e/`).

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

**Camera verification**

Camera verification (requirements specification, section 21) runs in a separate local program, the vision sidecar:

- Python, OpenCV and MediaPipe; PyTorch only if a custom model becomes necessary.
- Frames stay in the sidecar's memory. They are never written to disk, uploaded, or sent to the backend.
- The sidecar sends the backend observations only (signal type, confidence, interval), authenticated with its own token that works only on the camera endpoints.
- The backend applies the per-category profiles and the warning, grace and subtraction rules; the sidecar only observes.
- **Built so far** (`vision/`, see `vision/README.md`): the program itself, standard library only, with a `Detector` interface and a `ScriptedDetector` that replays a timeline instead of reading a camera. `CameraDetector` (`camera.py`, with the `camera` extra: OpenCV, and MediaPipe below 1.0) reads a webcam on a background thread that keeps only the latest frame, and reports `AWAY` when MediaPipe's BlazeFace finds no face at 0.5 or above, at a confidence from 1 (no face) down to 0.7 (a face just below 0.5). Where the profile checks `LOOKING_AWAY`, MediaPipe's Face Landmarker gives the head's yaw and pitch from its facial transformation matrix, and `calibration.py` compares them with the work area (`SCREEN`, or `SCREEN_OR_DESK`): beyond a 5° margin around the screen's, or the desk's, angles is `LOOKING_AWAY`. Those angles come from `focusquest-companion calibrate`, which stores only angles, never images, in `calibration.json` next to the pairing; until then fixed angles around straight ahead are used. A face lost within 2 seconds of being seen turned away stays `LOOKING_AWAY` until it is found again, for at most 5 seconds; after that it is `AWAY`. Where the profile checks `PHONE`, MediaPipe's object detector (EfficientDet-Lite0, COCO's "cell phone" class) looks for phones and, once one scores 0.25 or above, the hand landmarker for hands, both in the whole frame and again in two enlarged crops of its lower part, so a phone held further away or half out of the picture is not too small to score; a phone overlapping a hand is `PHONE` (`phones.py`), at a confidence rescaled from 0.7 at 0.25 to 1 at 0.6, because the detector's scores stay low even for a phone in plain view. `detect` receives the session's profile. The models are downloaded once and checked against pinned SHA-256s. It reports every 2 seconds, looks once a second, opens the detector only while the backend's answer says `cameraOn` (and closes it when it does not, or cannot be reached), stamps observations in the backend's clock from `serverTime`, and turns the backend's warnings into system notifications. When the backend's answer says `calibrate`, it calibrates with a guided window (`CameraCalibrator`) and reports how it went. On macOS, `focusquest-companion install` runs it in the background through a launch agent (`service.py`), started at login and restarted if it stops; with `--background` it waits to be paired rather than exiting. The agent runs it under `sandbox-exec` with a profile that refuses every outgoing connection except to this computer (`service.ONLY_THIS_COMPUTER`), whenever the backend is on this computer: MediaPipe 0.10 has Google's usage logging built in (task names and events, no pictures, to `play.googleapis.com`) with no switch to turn it off, and the sandbox refuses it. `install` downloads the models before, outside the sandbox. The real detector goes behind the same interface.
- **Measuring accuracy** (C1d): `focusquest-companion label --category …` writes one CSV row a second (the user's label from keys pressed in the camera window, what was reported, and the raw readings: face score, head angles, phone scores, hands) to `~/Documents/FocusQuest/`, numbers only. `focusquest-companion accuracy [FILE…]` replays those readings with today's thresholds or others (`--margin`, `--present-from`, `--phone-from`, `--certain-from`, `--min-confidence`, `--lost-for`), feeds them through the companion's `ObservationTracker` and a Python copy of `OffTaskCalculator`, and reports per signal how often it was found, how often it was right, and the warnings that would have come. Results are in `docs/camera_roadmap.md`.

The desktop app (`desktop/`, Tauri v2, macOS; see `desktop/README.md`) is a fourth local program:

- **A window and a menu bar icon on FocusQuest.** When it opens it asks `GET /api/auth/setup-status` on 127.0.0.1:8080. If FocusQuest answers (the Docker container or `bootRun`), the window opens it and the app leaves it running on quit. If nothing answers, it starts the copy bundled inside it (the boot jar and a `jlink`ed JRE, made by `desktop/scripts/prepare-backend.sh`) with `FOCUSQUEST_DATA_DIR=~/Library/Application Support/FocusQuest` and a JWT secret kept beside the data, and stops it with SIGTERM on quit. Either way FocusQuest stays on 127.0.0.1:8080, where the extension and the companion look for it; the two copies have separate data. The macOS menu adds View, Reload (Cmd+R), since the window keeps the web app it loaded until reloaded.
- **Blocked apps.** It reads `GET /api/extension/blocking-state` every 3 seconds with its own `fqd_` token and, every second, asks running apps matching `blockedApps` to quit (`NSRunningApplication.terminate`, as Cmd+Q), with a notification once per launch. Matching is by bundle id when the app has one, otherwise by name ignoring case. It never quits itself, the Finder, the Dock, the login window or SystemUIServer. When FocusQuest cannot be reached it keeps the last answer.
- **Blocked websites in Chrome** (21 October 2026). In the same one-second loop, while blocking is on, there is at least one block rule and Chrome (`com.google.Chrome`) is running, it reads every Chrome tab's window id, tab id and address through Apple events (`osascript -l JavaScript`, `chrome.rs`), matches each against `blockRules` and `allowRules` with a Rust port of the extension's matcher and precedence (`websites.rs`), and sets each blocked tab's address, by id, to the web app's `/blocked?site=…&reason=session|target` page, with a notification. Before redirecting it reads `GET /api/extension/current-session` once and adds the session's `status`, `task` and `remaining` seconds and the day's progress (`done` of `target` seconds) to the address, so the page (`BlockedView.vue`) shows the time left, counting down while the session is active, and a progress bar for today's streak, as the extension's blocked page does, without Chrome being signed in. Chrome is never asked anything while closed, since an Apple event would open it. macOS asks the user once to allow FocusQuest to control Chrome (`NSAppleEventsUsageDescription` in `src-tauri/Info.plist`); refused, the calls fail with -1743 and `desktop_status` reports `chrome: "denied"`, which Settings shows. Other browsers still rely on the extension.
- **Opens at login** from its first run as FocusQuest.app (`tauri-plugin-autostart`, a launch agent), re-registered on each run so it follows the app if it moves; **Open at Login** in the menu bar menu switches it off.
- **The web app inside its window** may call four app commands, and only from `http://127.0.0.1:8080/*` (`capabilities/web-app.json`): `desktop_status`, `desktop_connect`, `desktop_disconnect` (handing over and taking back the token, answers shaped like the extension's, plus `chrome`: `idle`, `ok`, `denied` or `error`) and `installed_apps` (the `.app` bundles in /Applications, /System/Applications and ~/Applications, and the running apps, with their bundle ids). Nothing else in the web app gets Tauri IPC.

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
9. The web app hands the Chrome extension its own long-lived token, limited to the extension endpoints (see "Extension sign-in" in section 9).
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

Passwords must never be stored in plaintext. Passwords must be hashed with BCrypt. BCrypt only handles the first 72 bytes and its encoder rejects anything longer, so setup accepts passwords of 8 to 72 characters and refuses more than 72 UTF-8 bytes with HTTP 400. Login answers 401 for a longer password, since none can have been set. Setup also refuses a time zone that `ZoneId` does not know: every streak calculation resolves it, so an unknown one would break the app after setup.

**JWT requirements**

- JWT signing secret must be provided through configuration or environment variables (`FOCUSQUEST_JWT_SECRET`, at least 32 bytes).
- The secret must not be committed to Git. No default secret ships with the code: when the variable is unset the backend generates a random secret for that run and logs a warning (never the value), so everyone is signed out whenever the backend restarts.
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

Origins are matched exactly (no patterns), and credentialed cross-origin requests are not allowed, because authentication is a bearer header and never a cookie. Preflight responses are cached for an hour. The extension does not depend on this list: it calls the backend with its manifest host permissions.

Allowed methods should be limited to:

```
GET, POST, PUT, PATCH, DELETE, OPTIONS
```

Allowed request headers should include only what is needed:

```
Authorization, Content-Type
```

**Response headers and development tooling**

- Responses carry Spring Security's default protective headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and `Cache-Control: no-store`.
- The H2 console is off by default, because it gives full access to the database, password hash included, behind an empty database password. Start the backend with `FOCUSQUEST_H2_CONSOLE=true` to use it. Only then does security let `/h2-console/**` through and relax `X-Frame-Options` to `SAMEORIGIN`, which the console needs.
- Every other unauthenticated request, including unknown paths, gets the same 401 JSON body, so routes cannot be enumerated.

**Logging**

`GlobalExceptionHandler` is the only place the backend logs a request failure. It logs the stack trace of unexpected exceptions and returns a generic message to the client. Nothing logs request bodies, passwords, password hashes, tokens or the signing secret, and `SecurityConfigurationIntegrationTest` checks that across login, failed login, bad tokens, malformed bodies and data deletion. The extension logger has the same rule (see `extension/src/utils/logger.ts`).

**Token storage**

- The frontend should avoid exposing long-lived tokens unnecessarily.
- The Vue frontend keeps the token in `sessionStorage`, so it is discarded when the browser tab closes. It also records the token's expiry time and treats an expired token as signed out without calling the backend. After a page reload only the token survives; the user profile is re-fetched from `GET /api/auth/me`, and a rejected token signs the user out.
- The Chrome extension should use chrome.storage rather than plain page local storage for extension-owned token storage.
- The application should not log bearer tokens.
- The extension must not inject tokens into ordinary website pages.

**Security-related future work**

- Password-change endpoint.
- Token refresh or short-lived access tokens.
- Expiry or rotation of the extension token (it currently lasts until it is replaced or revoked; see the extension sign-in decisions in section 9).
- Rate limiting, in particular of failed logins: the API is bound to 127.0.0.1, but nothing slows repeated guesses from another local process.
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
    StreakPeriodCalculator.java
    StreakController.java
    StreakConfigurationService.java   (edits to streak settings, refused while blocking is enforced)
    StreakProgressResponse.java, CurrentStreaksResponse.java
    StreakConfigurationResponse.java
    CreateStreakConfigurationRequest.java, UpdateStreakConfigurationRequest.java

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
    GemTransaction.java
    GemTransactionType.java
    GemTransactionRepository.java
    GemService.java
    Levels.java                       (the level curve)
    ProgressionService.java           (ties XP and gem rewards together)
    ProgressionController.java
    ProgressionResponse.java, ExperienceTransactionResponse.java, GemTransactionResponse.java

  export/
    ExportService.java
    ExportController.java
    LocalDataExportDto.java           (and the *Backup records it is made of)
    RestoreService.java
    RestoreController.java
    DataDeletionService.java
    DataDeletionController.java

  vision/
    CameraSettings.java               (consent and the default for new sessions)
    CameraSettingsService.java
    CameraSettingsController.java
    CameraProfiles.java               (the profile of each task category)
    CameraProfile.java, WorkArea.java, OffTaskSignal.java
    CameraProfileController.java
    CameraObservation.java            (one stretch of one off-task signal)
    OffTaskCalculator.java            (observations to episodes; pure)
    OffTaskService.java               (implements session.OffTaskAccounting)
    OffTaskInterval.java, OffTaskDispute.java
    OffTaskController.java
    CompanionCredentialService.java   (the companion program's scoped token and when it last reported)
    CompanionService.java, CompanionController.java

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
- Not implemented yet: `WebConfig`, `TimeCalculationService`. `UserController` delegates to `ProfileService` (same package), which needs the streak and blocking modules and so is kept out of `UserService`.
- The export, restore and deletion services read, write and delete other modules' data through those modules' repositories (`deleteAllByUser` and `findByUser...` queries) rather than through their services, since they need every row, not the filtered views the services expose.

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
- Complete a streak period, and pay its rewards.

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
- Displaying XP, level, gems, history, and settings.
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
      RuleForm.vue
      RuleList.vue
      RuleItem.vue

    progression/
      XpDisplay.vue
      GemDisplay.vue
      StreakFreezeCard.vue
      XpHistory.vue

  views/
    LoginView.vue
    SetupView.vue
    DashboardView.vue
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
    blockingStore.ts
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
- The backend keeps creating and starting separate (`PLANNED`, then `ACTIVE`), but the form's "Start focusing" does both in one step, so the timer and blocking begin at once. A planned session is still shown on its own when one is left over: after a reload, `fetchCurrent` fetches it back when nothing is running and none is held locally (so polling does not repeat the request), and when starting fails it stays with the error. "Change details" deletes it on the server and opens the form with its details; if the delete fails nothing is lost, since a planned session blocks nothing and the next one created replaces it. The form offers 25, 50 and 90 minutes plus "Custom", and starts from the category and duration last used in this browser (`utils/sessionDefaults.ts`, `localStorage`, a convenience only). Before starting, the planner states in plain text that websites stay blocked until today's daily target is reached (`blockingSentence` in `utils/streak.ts`).
- `SessionTimer` counts locally between server responses, from the moment each response arrived (`Date.now()` at receipt) rather than from the server's `generatedAt`, so browser clock skew cannot distort it. When the countdown reaches zero the dashboard re-fetches the session, and the Complete button is enabled from the server's `remainingFocusSeconds`, not the local countdown. The dashboard also re-fetches when the tab becomes visible again.
- Abandoning is labelled "End early", a quiet link, and asks for confirmation. The confirmation states the outcome rather than a conditional (`endEarlyOutcome` in `utils/streak.ts`): the time kept toward today's target, and whether that reaches it, so websites unblock now, or how many minutes are left. Afterwards the start page says how many minutes still counted toward today. When a fetch shows today's target newly reached, the dashboard remembers it until the next session starts, and the completion or start page names the streak day (`targetLine`); short of the target it says what is left. Manual override is offered only after a session has been abandoned with blocking still held: the session summary on the dashboard shows an "Override blocking" button, and the running and paused views have none. It opens `ManualOverrideDialog`, which warns that an XP penalty will be applied without stating an amount (the penalty is a server-side, configurable placeholder), and calls the override endpoint only after the user confirms. Because the abandoned session is otherwise lost on a reload, the store looks at the latest history entry when there is no running session and restores it if it is `ABANDONED` with blocking `ACTIVE`; that summary has no dismiss button while the override is available.
- The session views are `DashboardView` and `HistoryView`. The dashboard shows the current session (running, paused, or interrupted with Resume and End early in `InterruptedSessionView`); `SessionComplete` for a session just completed, with the XP it added (the progression balance before and after), the focused time and today's progress, until "Plan another"; or `SessionPlanner`. `TodayProgress` shows today's daily target and streak on the start page, the running and paused views and the completion. The backend credits a session's time to the streak only at pause, resume and end, so for a session that counts it adds the session's `uncreditedStreakSeconds`, and while it runs, the seconds since the session arrived; the streak is fetched again whenever the session changes. Complete appears only once the server reports no remaining time, as a secondary button beside Pause: past the planned time the student is assumed to be in flow, so `SessionTimer` keeps counting the time beyond the plan in the clock, says "Planned time reached" once in a live region, and puts the time (or "Paused") in `document.title`. A task session without a description is titled after its category ("Studying session"). The form starts on Studying the first time. `/sessions/new` redirects to the dashboard.

**Streaks, progression and settings in the frontend**

- `api/streakApi.ts`, `api/progressionApi.ts` and `api/settingsApi.ts` wrap the endpoints in section 10; `stores/streakStore.ts`, `stores/progressionStore.ts` and `stores/settingsStore.ts` hold the results. As with sessions, the frontend only displays what the backend reports: it never credits streak time, decides that a target is reached, or computes XP. The percentage bar and "time to go" figure in `StreakProgress` are display arithmetic on the backend's `qualifyingSeconds` and `targetMinutes`.
- `StreaksView` shows the level and XP (`XpDisplay`), the gem balance (`GemDisplay`), the current progress of each configured streak (`StreakProgress`) and one `StreakConfigurationForm` per period type. `StreakProgress` leads with the current streak in the streak's own unit ("5 days in a row" for a daily streak, "3 weeks in a row" for a weekly one) and what the user must do to extend it, then states how much time is counted against the target, how much is left, which sessions count, when the period resets, and any overtime. A user with both streaks sees both counts. The bars are `progressbar`s and the status is also written out, so colour is never the only signal.
- `api/blockingApi.ts` wraps the six block and allowlist endpoints and `stores/blockingStore.ts` holds both lists. `BlockingRulesView` (route `/blocking-rules`, "Blocking" in the navigation) shows the blocked and allowed sites as two `RuleList`s of `RuleItem`s, an add form under each (`BlockRuleForm`, `AllowlistRuleForm`, both thin wrappers over the shared `RuleForm`), and edit (a dialog around the same form) and delete (a confirmation) for every rule. A rule can also be deactivated: it is kept but the extension never receives it. `utils/blockingRules.ts` reproduces the backend's rule syntax so mistakes are caught before a request: protocols, query strings, fragments, ports, spaces, single-label hosts and malformed paths each get their own message, and a rule that normalizes to the same value as one already in the same list is rejected as a duplicate. This is a convenience only; the backend re-validates and remains the authority, and its message is shown when it refuses. The same site may appear in both lists (the allowlist wins a tie).
- The **Apps** tab of `BlockingRulesView` lists the blocked Mac apps (`AppList`, `AppForm`, `utils/blockedApps.ts`; the store's `addApp`, `updateApp`, `deleteApp`). Only names are shown. Inside the desktop app the name field suggests this Mac's apps (a `<datalist>` from `installed_apps`), and `appRequest` sends the bundle id of an installed app with that name; an edit keeps the app's own bundle id while the name stays the same and drops it on a rename.
- `stores/desktopStore.ts` connects the desktop app around the page, as `extensionStore` does the extension: `AppShell` calls `autoConnect` on every signed-in page, which, inside the desktop app (`utils/desktopBridge.ts`, `@tauri-apps/api`), issues a desktop token and hands it over unless the user disconnected it on purpose (`utils/extensionOptOut.ts`, per client). `SettingsView` shows a **Desktop app** row only inside the app, with Reconnect and Disconnect. Inside the app, which runs no browser extension, `AppShell` does not try to connect one and `SettingsView` leaves out the **Browser extension** row; in a browser it is the other way round.
- The view greys out what the configuration lock (section 10) would refuse while a session is running or paused, or an abandoned one still holds blocking: blocked sites can be added but not edited or deleted, and allowed sites can be deleted but not added or edited. As with streak settings this is only a hint, worked out from the session store; the backend's 409 is what enforces it, and its message is displayed if the two ever disagree.
- The streak count comes from the backend (`dailyStreak`, `weeklyStreak`, `dailyStreakProtectedDays`); the frontend never counts periods. `XpDisplay` shows the level, the XP total and the share of the current level completed, from the level bounds the backend reports, so the frontend does not know the level curve either.
- While website blocking is being enforced the settings forms are disabled and a notice says why. The page infers this from the session store (a running or paused session, or an abandoned one still holding blocking); the backend refuses the change with `409` regardless, and its message is shown if the page guessed wrong.
- The daily streak always exists, so its form always edits; the weekly form creates the streak the first time and edits it afterwards. The store picks create or update from whether a configuration for that period type is loaded, and re-loads the configurations when a save fails, so a weekly streak added from another tab does not send the next save down the wrong path. The view tells the user that a change applies only to periods that have not started.
- `GemDisplay` takes a `balance` and shows "Not available" for `null`, which is only the case before the progression request has finished, so the card never shows a made-up zero.
- `SettingsView` edits the display name and time zone (the username is shown read-only). The time zone select is disabled, with a note, while blocking is enforced, inferred from the session store as on the streaks page; the backend's `409` is what enforces it. Otherwise a note says a new time zone takes effect from the next day and week. It downloads the export as `focusquest-export-YYYY-MM-DD.json` (`utils/download.ts`). "Restore from file" reads a chosen file (`readBackupFile` in `settingsStore.ts` only checks it looks like an export), asks for confirmation naming the backup's date, posts it, then reloads the signed-in user and resets the session store so every view shows the restored data; a refusal is shown with the backend's message. It deletes all data behind a confirmation dialog whose default focus is "Keep my data". After a successful deletion the store signs the user out, which clears the other stores, and the view routes to `/setup`. If the backend refuses (blocking is active) the user stays signed in and sees the backend's message.
- `CameraVerificationCard` on the settings page turns camera verification on and off. While it is off it lists the consent points (`utils/cameraConsent.ts`), and the "Turn on" button stays disabled until "I have read this and agree" is ticked. While it is on it shows since when, and a checkbox for whether new sessions use the camera by default, saved on change. A notice says what the companion program checks (presence, where the user looks, a phone in hand) and to calibrate it once. Under the companion's status, a "Calibrate" button (disabled unless it is connected) asks it to calibrate; the card asks for the settings every 2 seconds while the calibration is `REQUESTED` or `CALIBRATING`, and also while camera verification is on but the program is not connected (it is paired and started in a terminal, outside the page, so Calibrate becomes available as soon as it connects), and shows when it was calibrated, "follow the steps in the camera window", or why it failed.
- **Off-task warnings in the web app.** While a camera-verified session is ACTIVE, the dashboard has `offTaskStore` poll `GET /api/focus-sessions/{id}/off-task` every 5 seconds (`OFF_TASK_POLL_MS`), so a warning shows within 5 seconds, well inside the 60-second grace period. Polling stops when the session pauses or ends, or the page is left. A failed poll keeps the last status. Polling was chosen over a server push channel: the backend is local, and the browser's `EventSource` cannot send the sign-in header.
  - `OffTaskBanner` in `ActiveSessionView` shows the warning with a countdown to `deductionStartsAt`, then that the time is not counting, each with a "Not accurate?" button that disputes the episode. A quieter notice says when the companion program is not connected.
  - The timer counts net time, active minus off-task, from whichever figure arrived last. It holds still while time is being subtracted. An "Off task so far" line appears once there is any.
- **Camera in the session screens.**
  - `SessionPlanner` loads the camera settings. While camera verification is on, `SessionForm` offers a "Verify with camera" switch. It starts at the user's default, follows that default if it loads late (until the user touches the switch), and sends `cameraVerification`. While camera verification is off, nothing is sent.
  - `SessionMeta` adds "Checked by camera" to a camera-verified session.
  - `SessionSummary` and the history show focused time net of off-task time, with the off-task time beside it.
  - In the history, a camera-verified session has an "Off-task episodes" list (`OffTaskEpisodes`). It is loaded when first opened and says what happened to each episode. Warned episodes of a session that is not completed can be disputed; the history then reloads.
- Not built yet: `StreakHistory` and `XpHistory`.
- `StreakFreezeCard` on the streaks page shows how many freezes the user holds out of the limit, what one costs, and a buy button that is disabled, with the reason, at the limit or when short of gems. It mentions the newest freeze spent in the last week, so the user learns one was used. After a purchase the store updates the gem balance from the answer and fetches the streak again. While freezes are bridging missed days, the daily streak's hint says so.

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
      navigationGuard.ts      (added in the foundation build; see "Extension implementation status")

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
      config.ts               (backend and frontend URLs, sync period)
      blockedPage.ts          (how the blocked URL is passed to the blocked page)

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

**Extension implementation status**

The extension foundation is built (see `extension/README.md` for how to load and test it). It follows the structure above, with these decisions:

- **Two layers of enforcement.** declarativeNetRequest dynamic rules redirect blocked page navigations to the blocked page; the browser applies them even while the service worker is asleep. A `webNavigation` listener (`navigationGuard.ts`) applies the exact matcher and precedence from `urlMatcher.ts` and `rulePrecedence.ts` behind them, to catch single-page-app navigations that make no page request (for example clicking into YouTube Shorts) and equivalent URL spellings a regex cannot express (percent-encoding, repeated slashes).
- **Permissions.** `declarativeNetRequest`, `storage`, `alarms`, `webNavigation`, `scripting`, and `<all_urls>` host access. Chrome only allows a redirect rule on sites the extension can access. Only the blocked page is web-accessible. The one content script, the web app bridge, runs only on the web app's origins and holds nothing, so the token is never reachable from website code. `scripting` adds the bridge to web app tabs already open when the extension is installed or reloaded.
- **Only top-level page navigations are blocked.** Embedded frames and sub-resources are left alone.
- **Fail closed.** If the backend is unreachable or rejects the token, the last known rules stay in force and the blocked page explains why. Only a successful synchronization that reports enforcement has ended removes blocking. This stops an expired token or a stopped backend being a way out of a session.
- **Synchronization.** A heartbeat every 30 seconds (the Chrome alarm minimum), on service worker start, browser start, install, and whenever the stored token changes. The web app also sends `focusquest.sync` after every successful session action (start, pause, resume, complete, abandon, override) and rule change, so blocking follows within a second or two instead of at the next alarm. The full state is re-fetched only when `stateVersion` changes.

**Extension sign-in**

The user never copies a token. Signing the extension in is a handoff from the web app:

- **A separate credential, not the web app's JWT.** `POST /api/auth/extension-token` (JWT required) issues an opaque random token prefixed `fqx_`. The backend stores only its SHA-256 hash, in `extension_credentials` (one row per user; issuing again replaces the old token, and `DELETE /api/auth/extension-token` revokes it). Because it is not a JWT it survives backend restarts even without `FOCUSQUEST_JWT_SECRET`, and it does not expire after 60 minutes, so a session of any length stays visible to the extension.
- **Scoped.** `JwtAuthenticationFilter` authenticates an `fqx_` token with `ROLE_EXTENSION` only. `SecurityConfig` lets that role reach `/api/extension/**` and nothing else; every other route needs `ROLE_USER`. A leaked extension token can read the blocking rules and current session but cannot start sessions, read history, export, restore or delete data, or mint tokens. Deleting all data removes it too.
- **The desktop app's own credential.** `POST /api/auth/desktop-token` issues an `fqd_` token in the same table, with `client` `DESKTOP` (the extension's rows are `BROWSER`; one per user and client since V19, so connecting one never signs the other out). `DELETE /api/auth/desktop-token` revokes it. It authenticates with `ROLE_DESKTOP`, which reaches only `GET /api/extension/blocking-state` and `GET /api/extension/current-session` (for its blocked page; reading it changes nothing): it cannot check in for sessions (`/api/extension/heartbeat`), so the desktop app never keeps a session watched. The web app hands it over inside the desktop app's window through Tauri IPC (`utils/desktopBridge.ts`, `stores/desktopStore.ts`), which the app allows only from `http://127.0.0.1:8080` and only for `desktop_status`, `desktop_connect` and `desktop_disconnect` (`desktop/src-tauri/capabilities/web-app.json`).
- **Handoff.** The extension runs a content script, the bridge (`content/webAppBridge.ts`), on the web app's origins only. It marks the page with `data-focusquest-extension`, so the web app knows at once whether an extension is listening, and relays requests between the page (`window.postMessage`) and the service worker. Unlike Chrome's `externally_connectable`, which it replaced, this works in every browser and needs no fixed extension id. The web app sends `focusquest.status`, `focusquest.connect`, `focusquest.disconnect` and `focusquest.sync` messages; the service worker (`externalMessages.ts`) checks the sender's origin again, accepts only `fqx_` tokens, stores the token in `chrome.storage.local`, runs a sync and replies with the result. The web app never stores the extension token.
- **Automatic.** After sign-in, `AppShell` asks the extension for its state and connects it if it is installed but not connected (once per page load, and never after the user disconnected it on purpose). Settings has Connect, Reconnect and Disconnect buttons.
- **Visible state.** A toolbar badge shows a red `!` when the extension is not connected and a grey `?` when it cannot reach the backend; the popup explains and links to Settings.

**Extension follow-up work**

1. **Extension icons** (`assets/icons/`).
2. **Token expiry.** The extension token never expires; reconnecting or disconnecting replaces or revokes it. Consider an expiry with silent renewal if the API ever leaves localhost.
3. **Extension login form.** The extension only connects through the web app, so the web app must be running. A login form in the popup would remove that need.
4. **Backend parity.** The backend's `TargetUrl` treats a URL with a malformed percent-escape (for example `%zz`) as "not a URL", so it is never blocked. The extension blocks it instead. Align the backend reference implementation with the extension.
5. **Non-ASCII path rules** (for example `/café`) are enforced only by the navigation guard, so a page may briefly begin to load before it is redirected. Add a percent-encoded variant of the rule to the declarativeNetRequest regex if this matters.

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
POST   /api/focus-sessions
GET    /api/focus-sessions/current
GET    /api/focus-sessions/planned
GET    /api/focus-sessions/{id}
GET    /api/focus-sessions/history
DELETE /api/focus-sessions/{id}

POST /api/focus-sessions/{id}/start
POST /api/focus-sessions/{id}/pause
POST /api/focus-sessions/{id}/resume
POST /api/focus-sessions/{id}/complete
POST /api/focus-sessions/{id}/abandon
POST /api/focus-sessions/{id}/override
```

Notes:

- `POST /api/focus-sessions` returns 201 with the planned session. A user has at most one `PLANNED` session: creating one deletes any earlier one that was never started. A planned session has no pauses, streak credit or XP, so nothing else refers to it.
- `GET /api/focus-sessions/planned` returns the `PLANNED` session, or 204 No Content. It is separate from `current`, which only ever holds a running or interrupted session, so a session that has not started never counts as enforcing blocking.
- `DELETE /api/focus-sessions/{id}` deletes a `PLANNED` session and returns 204. Any other status is refused with `INVALID_SESSION_STATE`. The session body is `FocusSessionDto`; `activeFocusSeconds` and `remainingFocusSeconds` are live (they include the segment still running as of `generatedAt`), and `uncreditedStreakSeconds` is the session's time not yet credited to the streak as of `generatedAt` (active time since the last credit, including the running segment, plus pauses finalized since).
- `GET /api/focus-sessions/current` returns the ACTIVE or PAUSED session, or the latest started session if it is INTERRUPTED (waiting to be resumed or abandoned), or 204 No Content. It first interrupts a running session whose heartbeat has lapsed (section 13).
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

Implemented: `GET /api/streaks/current`, `GET` and `POST /api/streak-configurations`, and `PUT /api/streak-configurations/{id}`. Not implemented: `GET /api/streaks/history` and `GET /api/streak-periods/current` (the current period is already in `streaks/current`).

- **`streaks/current`** returns `{ "daily": ..., "weekly": ..., "dailyStreak": n, "weeklyStreak": n, "dailyStreakProtectedDays": n }`. `dailyStreak` and `weeklyStreak` are the current streak lengths (see section 19); `dailyStreakProtectedDays` is how many missed days freezes are bridging until the next daily target is reached, 0 when none; `weekly` and `weeklyStreak` are null until a weekly streak is configured. Each period is `periodType`, `startTime`, `endTime` (exclusive), `targetMinutes`, `requiredTaskMode`, `requiredCategory`, `qualifyingSeconds` (capped at the target), `overtimeSeconds` and `status`. Before any time is credited in a period the values come from the active configuration with zero progress, and no period is stored, so a fresh day still shows its target. `weekly` is null until a weekly streak is configured.
- **`GET /api/streak-configurations`** returns the configuration in force for each period type: always the daily one, and the weekly one if configured. Each has `id`, `periodType`, `targetMinutes`, `requiredTaskMode` and `requiredCategory` (null means any category).
- **Lock:** `POST` and `PUT` are refused with `409 CONFLICT` ("Streak settings cannot be changed while website blocking is active") while blocking is being enforced (section 13): a running or paused session, or an abandoned one still holding blocking. Without it the user could lower today's target after committing to a session and abandon it once the lower target is met, releasing blocking without the override penalty. Reading is always allowed, and the lock lifts when the session completes or the blocking is overridden.
- **`POST`** configures a period type that has none. Since daily always exists, this is in practice the weekly streak; a type that is already configured is refused with `409 CONFLICT`. **`PUT`** changes `targetMinutes`, `requiredTaskMode` and `requiredCategory`; the period type cannot be changed. Another user's configuration is `NOT_FOUND`.
- **Validation:** `targetMinutes` must be 5 to 1440 for a daily streak and 5 to 10080 for a weekly one. A task-free streak cannot name a category, and `TASK_FREE` is not a valid required category. Failures are `400 BAD_REQUEST`.
- **Effect of a change:** the configuration is edited in place. Periods copy their settings when they are created (section 11), so a period already in progress keeps the old ones and the change governs periods that have not started. Until the first time is credited on a given day, that day has no period yet and picks up the new configuration.

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

GET    /api/blocked-apps
POST   /api/blocked-apps
PUT    /api/blocked-apps/{id}
DELETE /api/blocked-apps/{id}
```

Notes:

- Request body: `{ "targetValue": "example.com/docs", "displayName": "Docs", "active": true }`. Only `targetValue` is required. `displayName` defaults to the normalized rule; `active` defaults to true on create and to the current value on update.
- The rule type (`DOMAIN` or `URL_PATH`) is derived from `targetValue` and returned in the response; clients never send it.
- Creating returns 201; deleting returns 204. A rule that duplicates an existing one after normalization is rejected with `DUPLICATE_RULE`.
- **Configuration lock:** while blocking is being enforced (see section 13), the configuration may only get stricter. Creating a block rule and deleting an allowlist rule are allowed; editing or deleting a block rule, and creating or editing an allowlist rule, are refused with 409.
- **Blocked apps** are Mac applications the desktop app keeps closed, on the same terms as blocked sites (there is no allowlist for apps). Request body: `{ "name": "Slack", "bundleId": "com.tinyspeck.slackmacgap", "active": true }`. Only `name` is required (at most 100 characters, trimmed); a blank `bundleId` is stored as null and otherwise must be a bundle id (parts of letters, digits and hyphens joined by dots, at least two parts). An app is matched by its bundle id when it has one, and otherwise by its name, ignoring case. Two apps with the same name in any case are a `DUPLICATE_RULE`. The configuration lock treats them like block rules: they can be created while blocking is enforced, but not edited or deleted. The web app shows only the name; it keeps the bundle id on an edit that keeps the name, and drops it on a rename.

**Progression**

```
GET  /api/me/progression
GET  /api/me/xp-history
GET  /api/me/gems
GET  /api/me/gem-history
GET  /api/me/streak-freezes
POST /api/me/streak-freezes/purchase
```

Implemented: `GET /api/me/progression`, which returns `{ "totalXp", "level", "levelStartXp", "nextLevelXp", "gems" }`. `totalXp` is the sum of the user's XP ledger, floored at zero, so override penalties never show as a negative balance. `levelStartXp` and `nextLevelXp` are the total XP at which the current level began and the next begins, so a client can draw progress through the level without knowing the curve. `gems` is the sum of the gem ledger. The history endpoints (`xp-history`, `gem-history`, and the `gems` shortcut) are not implemented.

`GET /api/me/streak-freezes` returns `{ "owned", "maxOwned", "price", "gems", "recentlyUsed" }`: unused freezes held, the limit (2), the price in gems (10), the gem balance, and up to five spent freezes, newest first, each `{ "periodStart", "periodEnd", "usedAt" }`. `POST /api/me/streak-freezes/purchase` buys one and returns the same shape. It is refused with `409 CONFLICT` at the limit ("You already hold 2 streak freezes, the most allowed") or when short of gems ("A streak freeze costs 10 gems and you have N"). The price and limit are configuration (`focusquest.streak-freeze.price`, `max-owned`).

**Settings**

```
PUT    /api/me/profile
GET    /api/me/camera-settings
PUT    /api/me/camera-settings
GET    /api/camera/profiles
GET    /api/focus-sessions/{id}/off-task
POST   /api/focus-sessions/{id}/off-task/disputes
POST   /api/me/companion-token
DELETE /api/me/companion-token
POST   /api/companion/heartbeat          (companion token only)
POST   /api/companion/observations       (companion token only)
GET    /api/export
POST   /api/me/data/restore
DELETE /api/me/data
```

- **`PUT /api/me/profile`** takes `{ "displayName", "timezone" }` (both required, at most 100 and 50 characters; the username cannot be changed) and returns the updated `UserDto`. An unknown time zone is `400` ("Unknown time zone"). Changing the time zone is refused with `409 CONFLICT` while website blocking is being enforced (section 13), like streak settings, because moving midnight could end the day early and release blocking. The display name can always be changed. A new time zone takes effect from the next daily and weekly period (see "Daily and weekly boundaries" in section 13); session timestamps are instants and are not changed.

- **`GET /api/me/camera-settings`** returns `{ "enabled", "consentVersion", "consentedAt", "verifyNewSessionsByDefault" }`. `consentVersion` is the version of the consent text the backend requires (`CameraSettingsService.CONSENT_VERSION`, currently 1); `consentedAt` is null while camera verification is off. **`PUT`** takes `{ "enabled", "consentVersion", "verifyNewSessionsByDefault" }`. Turning it on needs `consentVersion` equal to the current version, otherwise `400` ("Read and accept the current camera consent text to turn camera verification on"); once on, changing only the default keeps the original consent. Turning it off withdraws consent. Consent to an older version of the text does not count, so bumping the version asks everyone again. The web app's `utils/cameraConsent.ts` holds the text and must carry the same version.
- **`GET /api/camera/profiles`** returns what the camera checks for each task category, one entry per category in declaration order: `{ "category", "workArea", "checks": [{ "signal", "warningAfterSeconds" }], "graceSeconds", "minConfidence" }`. `workArea` is `SCREEN`, `SCREEN_OR_DESK` or `ANYWHERE`; `checks` lists `AWAY`, `PHONE` and, unless the work area is `ANYWHERE`, `LOOKING_AWAY`. The work area of each category is fixed in `CameraProfiles`; the timings and minimum confidence come from `focusquest.camera.*` in `application.yml` and are the same for every category. The profiles are the same for every user, but the route still needs sign-in. The settings page lists them, grouped by identical rules, in a collapsible section of `CameraVerificationCard`.
- **Camera-verified sessions.** `POST /api/focus-sessions` takes an optional `cameraVerification`. Omitted, it takes the user's default: on if camera verification is on and new sessions use it by default. Asking for it while camera verification is off is `400`. The session DTO carries `cameraVerification` and `offTaskSeconds`, the settled plus provisional off-task time, and `remainingFocusSeconds` counts it.
- **`GET /api/focus-sessions/{id}/off-task`** returns `{ "sessionId", "state", "companionConnected", "cameraProblem", "offTaskSeconds", "current", "deductionStartsAt", "episodes" }`. `deductionStartsAt` is set once the user is warned: when subtraction starts if they stay off task (the warning plus the grace), or when it started. The companion's answer carries the same value.
  - `state` is `NOT_VERIFIED`, `NOT_RUNNING`, `NOT_CONNECTED`, `CAMERA_UNAVAILABLE`, `ON_TASK`, `OFF_TASK`, `WARNED` or `DEDUCTING`. An episode seen within the last 15 seconds counts as going on, and it is `current`.
  - Each episode is `{ "startedAt", "endedAt", "warnedAt", "deductionStartedAt", "deductedSeconds", "disputed" }`. `deductedSeconds` counts active time only: what is settled plus what is provisional in the stretch not credited yet.
- **`POST /api/focus-sessions/{id}/off-task/disputes`** takes `{ "episodeStartedAt" }` and returns the same status.
  - `404` for an episode that does not exist.
  - `400` for a completed session, or one the camera does not check.
  - Disputing twice changes nothing.
- **Calibrating the companion program from Settings.** `POST /api/me/companion-calibration` asks for a calibration and returns the camera settings. `409` unless camera verification is on, the program is paired and connected, and no camera-verified session is ACTIVE (that session has the camera).
  - The program's next answer has `calibrate: true`. It reports on `POST /api/companion/calibration` with `{ "outcome": "STARTED" | "CALIBRATED" | "FAILED", "calibratedAt", "message" }` (`204`): `STARTED` as it begins, then `CALIBRATED` with when, or `FAILED` with why. It also sends `CALIBRATED` with its saved calibration's time whenever it connects, since pairing again starts a new credential. `calibratedAt` is never later than the backend's clock. The angles never leave the computer.
  - Stored on the companion's row in `companion_credentials` (V15): `calibration_requested_at`, `calibration_started_at`, `calibrated_at`, `calibration_failure`.
  - `CameraSettingsResponse.companion.calibration` is `{ "state", "calibratedAt", "failure" }`, worked out by `CompanionCalibration.of`: `REQUESTED` and `CALIBRATING` turn into `FAILED` after 2 minutes without progress (the program stopped, or was never running); otherwise `FAILED` with the program's message, `CALIBRATED`, or `NOT_CALIBRATED`. `calibratedAt` keeps the calibration in use when a new attempt fails.
- **Pairing the companion program.** `POST /api/me/companion-token` issues its token, `{ "token" }`. The token starts `fqc_` and replaces any earlier one; only its SHA-256 hash is stored, in `companion_credentials`. `DELETE` unpairs it.
  - The filter authenticates a `fqc_` token with `ROLE_COMPANION` only. `SecurityConfig` lets that role reach `/api/companion/**` and nothing else.
  - Unlike the extension's routes, `/api/companion/**` refuses the web app's JWT and the extension's token with `403`: only the paired program may report what the camera saw.
  - "Delete all data" removes the pairing; export and restore leave it out, like the extension token.
- **`POST /api/companion/heartbeat`** takes an optional `{ "cameraProblem" }`, records that the program reported, and returns `{ "cameraOn", "calibrate", "sessionId", "profile", "state", "warnedAt", "deductionStartsAt", "serverTime" }`.
  - `cameraOn` is true only while the user's current session is ACTIVE, camera-verified, and camera verification is on. Only then are the other fields set.
  - `calibrate` asks the program to calibrate now: requested in Settings, not yet started, within 2 minutes, and never while `cameraOn`.
  - `profile` is the category's `CameraProfileResponse`: what to look for. `state` with `warnedAt` and `deductionStartsAt` is what the program needs to show the warning.
  - `serverTime` is the backend's clock, always set. The program stamps its observations in it, since none may be more than 5 seconds ahead of it.
- **`POST /api/companion/observations`** takes `{ "sessionId", "observations": [{ "clientEventId", "signal", "confidence", "startedAt", "observedUntil" }], "cameraProblem" }` (`cameraProblem` optional). It counts as a heartbeat and returns the same answer. The observations go to `OffTaskService.recordObservations`, which accepts:
  - only running, camera-verified sessions of the user, with camera verification on (`404` for another user's session, otherwise `400`/`409`);
  - at most 200 observations at a time, none claiming a time more than 5 seconds ahead of the backend's clock.

  Sending a `clientEventId` again extends that stretch. Changing its signal or start is refused.
- **Connected.** The program counts as connected if it reported within the last 30 seconds (`CompanionCredentialService.CONNECTED_WITHIN`).
  - `GET /api/me/camera-settings` includes `companion`: `{ "paired", "pairedAt", "lastSeenAt", "connected" }`.
  - The off-task status includes `companionConnected`. Its state is `NOT_CONNECTED` while a camera-verified session runs and nothing is reporting.
  - The settings page pairs and unpairs in `CameraVerificationCard`. It shows the code once, with a copy button, and whether the program is connected.
- **A camera the program cannot use.** While a session wants the camera and the program cannot use it (no permission, taken by another app, unplugged, no pictures coming), each heartbeat or report carries `cameraProblem`, the program's message for the user (at most 300 characters). It is kept in `companion_credentials.camera_problem` (V16); a heartbeat or report without it clears it, and a calibration report leaves it alone.
  - In a running camera-verified session, a connected program with a problem puts the status in `CAMERA_UNAVAILABLE`, with the message in `cameraProblem` (null in every other state). `NOT_CONNECTED` wins over it: a program that stopped reporting says nothing about the camera. An episode still going on (seen within 15 seconds) is shown as it is until it ends.
  - The companion's own answer carries the same state. Nothing is observed meanwhile, so nothing is subtracted (requirements §21, "Failure").
  - `OffTaskBanner` shows "The camera isn't checking this session." followed by the message.

**Off-task time model.**
- **Episodes are computed, never stored.** `OffTaskCalculator` works them out from the observations each time.
- **Settling.** When `SessionService` credits a stretch of active time to the streak (resume, complete, abandon, interruption), it calls `OffTaskAccounting.settle` for that stretch. The stretch is `[creditedUntil - paused - active, creditedUntil - paused)`: the active segment since the previous credit, which is contiguous because a credit happens at every resume. `settle` stores the part of each episode's subtraction inside the stretch as an `OffTaskInterval` and returns the ranges.
- **Effect on the session.** The ranges are added to `FocusSession.offTaskSeconds`, so qualifying is active plus paused minus off-task. They are passed to `StreakService.recordContribution`, which leaves them out of each period part they fall in, so off-task time across midnight or the start of the week is taken from the period it fell in (to within a second, since credit times can fall between whole seconds).
- **Completion.** `completeSession` checks active minus settled minus provisional off-task against the plan. It credits (settles) before marking the session completed, so overtime is net.
- **Disputes.** A dispute stores an `OffTaskDispute` keyed by the episode's start. Settled intervals of that episode are marked disputed, and their seconds are restored to the session and credited to the streak at the time they fell.
- **Late observations.** Observations that arrive after their stretch was settled change nothing already settled.

- **`export`** returns everything held for the caller as one JSON document, complete enough to restore: `exportedAt`, `schemaVersion` (currently `2.4`), `user` (never the password hash), `focusSessions`, `sessionPauses`, `streakConfigurations`, `streakPeriods`, `streakContributions`, `streakFreezes` (each with `usedPeriodId`, null while unused), `experienceTransactions`, `gemTransactions`, `blockedTargets`, `allowlistTargets`, `blockedApps`, `cameraSettings` (null if never set), `cameraObservations`, `offTaskIntervals` and `offTaskDisputes`. Sessions carry `cameraVerification` and `offTaskSeconds`. Records carry their stored values, including the streak bookkeeping on sessions (`streakCreditedActiveSeconds`, `streakCreditedPausedSeconds`) and each period's `configurationId` and `freezeConsumed`, and refer to one another by the exported ids. A session's times are the stored totals, so a session still running at export time leaves out its segment in progress. New kinds of data get a new schema version and must be restored too.
- **`POST /api/me/data/restore`** takes an export and replaces all of the caller's data with it, returning 204. The account's username, password, extension token and desktop token are kept; the display name and time zone come from the backup. Rows get new ids and the references between them are rewritten, including ledger entries' `referenceId` for `FOCUS_SESSION`, `STREAK_PERIOD` and `STREAK_FREEZE` (a `LEVEL` reference is a level number and is kept). A session that was `ACTIVE` or `PAUSED` in the backup comes back `INTERRUPTED` with blocking `TECHNICAL_RELEASE`, and its open pause is closed with zero length, as if the extension had gone silent: it can be resumed or abandoned. The restore is all or nothing. It is refused with `400` for a schema version other than `2.0` to `2.4` (exports before `2.0` lack pauses and contributions; an older backup restores without the freezes, camera settings, off-task data or blocked apps it predates), an unknown time zone, missing or repeated ids, a reference to a record not in the file, duplicate rows the database would reject, or a missing required value; and with `409 CONFLICT` while website blocking is being enforced, for the same reason as deletion.
- **`DELETE /api/me/data`** deletes all of the caller's sessions, pauses, streak contributions, periods and configurations, XP and gem transactions, block and allowlist rules, and the account itself, and returns 204. The next launch is first-time setup, and the old token is rejected with 401. It is refused with `409 CONFLICT` while website blocking is being enforced (section 13); otherwise it would be a way to release blocking without the override penalty. The user must end the session, or abandon it and override, first.

**Extension-specific API**

The extension uses three endpoints, authenticated with its own scoped bearer token (see "Extension sign-in" above; a normal JWT is also accepted):

```
GET  /api/extension/blocking-state
GET  /api/extension/current-session
POST /api/extension/heartbeat
```

- **`blocking-state`** returns `enforcementActive`, `sessionId`, `blockingState`, `stateVersion`, `generatedAt`, and the active `blockRules`, `allowRules` and `blockedApps`. Each rule carries its `targetType`, canonical `targetValue`, pre-split `host` and `path` (null for a domain rule), and `displayName`. Each app is `{ id, name, bundleId, active }`, as in `/api/blocked-apps`. When enforcement is not active, all three lists are empty and clients should remove any blocking they have installed.
- **`current-session`** returns what the blocked page shows: session id, status, blocking state, task description, planned/active/remaining seconds, start time, and today's daily streak progress (`qualifyingSeconds`, `targetSeconds`, `status`). It returns 204 No Content when nothing is being enforced. When sites are blocked only by the unmet daily target, the session fields are null and the seconds are 0. `dailyStreak` shows zero progress before time is first credited that day.
- **`heartbeat`** accepts an optional `{ "stateVersion": "..." }` and returns `serverTime`, `enforcementActive`, `sessionId`, the current `stateVersion` and `refreshRequired`. The extension calls it periodically and re-fetches `blocking-state` only when `refreshRequired` is true. It also records the check-in on the user's running session (`lastHeartbeatAt`), after first checking the gap since the previous one, so a heartbeat that ends a long silence interrupts the session and returns the released state in the same response.
- `stateVersion` is a fingerprint of the enforcement flag, the enforcing session and its blocking state, and the active rules and apps. It changes exactly when the extension must re-synchronize, and is not affected by a pause or resume, which do not change blocking.

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
- lastHeartbeatAt
```

The last four are internal bookkeeping. `lastHeartbeatAt` is when the extension last checked in while the session was running; it is null until the first check-in, which leaves interruption detection disarmed (section 13). `activeSegmentStartedAt` marks when the current uninterrupted ACTIVE stretch began, so elapsed time can be added across pause/resume cycles. The two `streakCredited...` fields record how much of the session's time has already been credited to the streak, so each credit covers only the time since the previous one (see section 13).

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

The `type` is `SESSION_COMPLETION` (`referenceType = FOCUS_SESSION`), `STREAK_COMPLETION` (`referenceType = STREAK_PERIOD`) or `MANUAL_OVERRIDE_PENALTY` (`FOCUS_SESSION`, negative `amount`). A unique index on `(userId, type, referenceType, referenceId)` guarantees each session or period is paid, or penalized, at most once. See section 19 for the amounts.

**GemTransaction**

```
GemTransaction
- id
- userId
- amount
- type
- referenceType
- referenceId
- createdAt
```

The `type` is `LEVEL_UP` (`referenceType = LEVEL`, `referenceId` the level number) or `STREAK_COMPLETION` (`referenceType = STREAK_PERIOD`). The balance is the sum of the entries. A unique index on `(userId, type, referenceType, referenceId)` means a level, or a streak period, pays gems once, even if the level is lost and reached again. Spending would be a negative `amount`; nothing spends gems yet.

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
- Work out the current streak length for a period type (section 19).
- Trigger the streak rewards when a period reaches its target.

**BlockingService**

Responsibilities:

- Store block and allowlist rules: create, update, delete and list, with normalization, duplicate detection and per-user ownership.
- Refuse rule changes that would loosen blocking while enforcement is active.
- Determine whether enforcement is active, derived from the latest started session's status (section 13).
- Build the state the extension synchronizes: rules, `stateVersion`, and the blocked-page session details.
- Apply the matching and precedence rules to a URL (`evaluateUrl`), for tests and later UI use.

**StreakConfigurationService**

Responsibilities:

- List, create and update streak configurations through `StreakService`.
- Refuse creating or updating while website blocking is being enforced (section 10).

**ExperienceService**

Responsibilities:

- Award session-completion XP and streak-completion XP, each exactly once per session or period (section 19).
- Apply manual-override penalties (once per session, amount from `focusquest.xp.manual-override-penalty`).
- Report the XP total: the ledger sum, floored at zero.

**GemService**

Responsibilities:

- Grant gems for levels reached and for streak periods that reached their target, each exactly once.
- Report the gem balance: the ledger sum.
- Validate purchases, once there is something to buy.

**ProgressionService**

Responsibilities:

- Be the single entry point for rewards: `SessionService` reports a completed session and `StreakService` a streak period that reached its target, and nothing else touches the ledgers except the override penalty.
- After every XP award, grant the gems for any level it earned, in the same transaction as the event.
- Report the user's standing: XP, level and its bounds, and gems.

**ExportService**

Responsibilities:

- Export all local data as JSON, complete enough to restore.

**RestoreService**

Responsibilities:

- Replace the user's data with an export, rewriting the references between rows to the new ids.
- Refuse a file it cannot restore, or any restore while blocking is enforced, without changing anything.

**DataDeletionService**

Responsibilities:

- Refuse deletion while website blocking is being enforced.
- Delete every row the user owns, in foreign-key order (contributions, pauses, periods, sessions, configurations, XP transactions, rules), and then the account, in one transaction.

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
INTERRUPTED -> ACTIVE
INTERRUPTED -> ABANDONED
```

COMPLETED and ABANDONED are terminal. INTERRUPTED can be resumed or abandoned (see the interruption rule below).

**Interruption rule**

- A running session is interrupted when it is being watched and the extension has been silent for longer than `focusquest.session.heartbeat-timeout` (3 minutes; the extension checks in every 30 seconds, session or not). Every heartbeat also stamps `User.lastExtensionHeartbeatAt`. A session starting or resuming within the timeout of that stamp is watched from that moment (its `lastHeartbeatAt` is set to the start), so quitting the browser before the extension's first check-in for the session is caught. Otherwise it is watched from the extension's first check-in during it, so a session run without the extension is never interrupted.
- Detection is lazy: `SessionService` checks the gap on every heartbeat (before recording it), on `GET /api/focus-sessions/current`, and before every transition. A backend that was down is therefore caught on the first request after it restarts.
- The cutoff is the last heartbeat. The open ACTIVE segment is banked, or the open pause finalized, up to the cutoff (an interval that began after the cutoff contributes nothing), and that time is credited to the streak. Time after the cutoff is dropped. Blocking becomes `TECHNICAL_RELEASE`.
- Pause and complete are refused while interrupted. Resume makes the session ACTIVE and enforces blocking again, watching it at once if the extension is alive (as at start); it is allowed only while the session is still the user's latest started session. Abandon ends the session and leaves blocking released.
- Starting a new session is allowed while one is interrupted, and supersedes it.

**Pause rule**

- Starting a pause records startedAt.
- An unresolved pause contributes nothing to the streak yet.
- Resuming, abandoning, interrupting, or finalizing closes the pause.
- A finalized pause contributes to qualifying time if the session matches the streak configuration.
- The pause is finalized, and credited, when the session is resumed or abandoned, or at the last heartbeat when the session is interrupted.
- Paused time never counts toward the required active focus duration for session completion.
- Website blocking remains active throughout the pause.

**Streak crediting rule**

Streak progress is credited at two moments:

1. **On resume:** the total time already passed in the session (the active time before the pause plus the pause just finalized) is credited to the current daily and weekly periods at once, so the day's total is up to date while the session continues.
2. **When the session ends** (complete, abandon, override): only the time since the last credit is credited.

The session records what it has already credited, so no time is counted twice. An unresolved pause contributes nothing. An interruption credits the time up to the last heartbeat and drops the time after it.

Each credit covers one unbroken stretch of time: the active time since the previous credit, then the pause just finalized, if any. It ends when the credit is made, or at the last heartbeat for an interruption. `StreakService.recordContribution` splits that stretch at period boundaries (midnight, and Monday 00:00 for weekly) in the user's time zone, and credits each part to the period it was spent in, with one `StreakContribution` per period. A session from 23:40 to 00:20 gives 20 minutes to each day, and a pause that spans midnight is split the same way. A period that has already ended still takes its share: if that brings it to its target it is marked `COMPLETED` then and pays its streak rewards, once. A past period with no record yet is created from the configuration in force when the time was spent; if the user had not configured that period type then, the part is not credited to it.

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
- Otherwise keeps websites blocked until today's daily target is reached, or the user overrides that abandoned session. Starting a new session supersedes the abandoned one.

**Manual override rule**

A manual override:

- Releases website blocking immediately and for the rest of the day (or until another session starts), with blocking state `OVERRIDE_USED`. The next day starts blocked again.
- Is allowed only for a session that is already ABANDONED and still holding blocking (blocking state `ACTIVE`), that was abandoned today and is the user's latest started session, while today's daily target is unmet. It is refused for ACTIVE, PAUSED, PLANNED and COMPLETED sessions and cannot be applied twice.
- Sets overrideUsed = true on the abandoned session. The session's time was already credited to the streak when it was abandoned, so an override credits nothing further.
- Applies an XP penalty as a negative `ExperienceTransaction`, once per session. The amount is a placeholder (10) set in `focusquest.xp.manual-override-penalty`.
- Stores the audit trail as `overrideUsed`, the blocking state and the transaction (a dedicated `BlockingOverride` record is planned).

**Enforcement and release rule**

Websites are blocked from the start of each day until the daily target is reached, whether or not a session has been started, and throughout any running session. `BlockingService.isEnforcementActive` decides it from today's daily period and the user's most recently started session:

| Latest session | Enforcing? | Holds enforcement? |
| -------------- | ---------- | ------------------ |
| ACTIVE or PAUSED | Yes, even once the daily target is reached | Yes |
| ABANDONED today, blocking `ACTIVE` | Yes, until the daily target is reached | Yes |
| ABANDONED today and overridden | No, for the rest of the day | No |
| COMPLETED, INTERRUPTED, PLANNED, none, or abandoned on an earlier day | Yes, until the daily target is reached | No |

A session that *holds* enforcement (`BlockingService.findEnforcingSession`) is the one the extension's blocked page reports, and while one exists the configuration lock (section 10) and the streak-settings, deletion and restore locks apply. Blocking that comes from the unmet daily target alone has no session: `sessionId` is null, and the rules can still be edited, so a mistaken rule can be fixed during the day. Completing a session sets `RELEASED` and an interruption sets `TECHNICAL_RELEASE`; either way the session lets go, and only the unmet daily target can keep sites blocked.

When a session is abandoned, the blocking state is set to `RELEASED` if the daily target is reached afterwards, otherwise it stays `ACTIVE`. The backend keeps one blocking configuration per user (the active block and allowlist rules) rather than a per-session snapshot; the configuration lock in section 10 stops it being loosened during enforcement.

**Daily and weekly boundaries**

- Daily periods use the local calendar day in the user's configured time zone.
- Weekly periods run from Monday at 00:00 through Sunday at 23:59:59 in the user's configured time zone.
- A stored period keeps the boundaries it was created with. Before the time zone changes, `StreakService.keepCurrentPeriods` stores the current daily and weekly periods, so they run to their end under the old zone. The first period under the new zone starts exactly where the last stored one ended and runs to the new zone's next boundary; if that would be less than half a normal period, it runs to the boundary after that instead. Moving from UTC to New York, the first New York day is 28 hours; moving from UTC to Tokyo, it is 15. There is never a gap or an overlap.
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
- HTTP-level integration tests (`integration/`, built on `support/ApiIntegrationTest`) that drive the running application through MockMvc with the real security filter chain, controllers, services and an in-memory database, and a controllable clock:
  - `SessionWorkflowApiIntegrationTest`: first-launch setup through a full session (start, pause, resume, complete), abandon and override, planned sessions (fetched again, replaced, discarded), rule locking, error shapes, isolation between users, and export and deletion.
  - `StreakCalculationApiIntegrationTest`: daily and weekly streaks growing and breaking, day and week boundaries in the user's time zone, sessions and pauses split across midnight and the start of the week, overtime, and configuration validation.
  - `ProfileApiIntegrationTest`: editing the display name and time zone, validation, the time-zone lock while blocking is enforced, and a new time zone taking effect from the next day and week without breaking the streak.
  - `SecurityConfigurationIntegrationTest`: every route's authentication requirement, expired, forged, tampered and unsigned tokens, login enumeration, password and time zone limits, CORS, response headers, and secrets in the logs.
  - `RetriedRequestApiIntegrationTest`: each session transition sent a second time is refused and changes nothing, compared through the full export and the progression: no time moved or credited twice, no reward or penalty repeated.
- `RestartRecoveryIntegrationTest` stops and starts the whole application against the same database file in a temporary directory: completed work and a paused session survive, a session the extension was watching when the application went down is interrupted at its last heartbeat, and one run without the extension keeps running.
- `export/RestoreIntegrationTest` restores exports against a real database: a round trip reproduces everything but the ids, references point at the restored rows, a running session comes back interrupted, and every refusal (blocking enforced, older format, broken reference, duplicate, unknown time zone, missing value) leaves the data as it was.
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

Covered by the suite: `validation.test.ts` (all form rules), `LoginView`, `SetupView`, `SessionForm` and `SessionPlanner` (validation messages, submitted payloads, backend and network errors, double-submit protection, safe redirects), `apiError` (how any failure becomes a message), `tokenStorage` (corrupt or blocked storage), the common form and dialog components, and the active, paused and override views.

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

Built in `e2e/` (see `e2e/README.md`). Playwright drives Chromium with the extension loaded against the real backend, run from the test sources with the `e2e` profile: its own ports, an in-memory database, and two test-only hooks, one that resets the database and one that moves the clock forward. The suite covers:

1. First-launch setup, which signs the user in.
2. The extension connecting itself after sign-in.
3. Adding a block rule, then creating and starting a session.
4. The extension blocking the site, while running and while paused, within 10 seconds (so without waiting for its 30-second check-in).
5. Completing a session: blocking released and XP awarded.
6. Abandoning below the daily target: blocking kept, then released by a manual override, with the penalty recorded in history.
7. The rule lock: a rule cannot be deleted during a session, but one can be added.
8. Camera verification through the real companion program (`vision/`), paired from Settings and run with a scripted detector: a phone in hand warns in the web app and the program, then the time stops counting; a dispute gives it back; pausing closes the camera; when the program stops, the session goes on unverified; while the camera cannot be used (a scripted `unavailable` step), the session screen says so and why, until it can; and "Calibrate" in Settings reaches the program, which, having no camera, says why it cannot.

9. Signing in: a signed-out user sent from a page to the sign-in form, a wrong password refused, the right one returning to the page asked for, and the sign-in lasting a reload.
10. Streak progress: the daily streak's minutes and status on the Streaks page, a reached day counting, and the count carrying into the next day.
11. Interruption: with the clock moved past the heartbeat timeout, the session is interrupted, the unmet daily target keeps the site blocked, and Resume picks the session up again.

Not covered end to end: the desktop app (its own Rust tests, and tried by hand), and Firefox (checked by hand; see `docs/platform_roadmap.md`).

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
| Desktop app blocking | Done on macOS: the Tauri v2 app in `desktop/` quits blocked apps and closes blocked websites in Chrome. Windows needs a Win32 process watcher |
| ---                  | ---                                                                                     |
| More browsers        | Done: Chromium, Firefox and Safari from one source, with a manifest per browser; Safari's ships in its own small app, "FocusQuest for Safari" |
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

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmP4//8/AwAI/AL+GwXmLwAAAABJRU5ErkJggg==)

**19\. XP and gem economy**

The economy rewards effort and consistency, in the manner of Duolingo: XP for the work itself, a bonus for hitting the daily goal, levels that get slower to climb, a second currency (gems) for milestones, and a streak that a missed day ends. All amounts below are configuration in `application.yml` (`focusquest.xp` and `focusquest.gems`) except the level curve, which is a fixed rule of the game.

**Principles**

- **Focus is the unit of value.** One XP is one planned minute of a completed session, so the numbers are easy to reason about.
- **Finishing what you planned matters more than showing up.** Only a completed session earns session XP. An abandoned session earns none, though its time still counts toward the streak and can still earn the streak reward.
- **Consistency is rewarded on top of effort.** Reaching a daily or weekly target pays a bonus, once per period.
- **Nothing pays twice.** Every award is a ledger entry tied to what earned it (a session, a streak period, a level) under a unique index, so retries, and levels lost and regained, never pay again.
- **Overtime is not farmed.** Time beyond the plan or the target earns nothing extra.

**XP**

| Event | XP | Setting | Once per |
| ----- | -- | ------- | -------- |
| Complete a session | 1 per planned focus minute | `xp.per-focus-minute` | session |
| Daily streak target reached | +10 | `xp.daily-streak-bonus` | day |
| Weekly streak target reached | +50 | `xp.weekly-streak-bonus` | week |
| Override blocking on an abandoned session | -10 | `xp.manual-override-penalty` | session |

The session award is for the planned length, and completing requires having focused the whole plan, so a 25 minute session is worth 25 XP whether it took 25 minutes or 30. The override penalty equals ten focused minutes. It is recorded in full, but the XP total shown is floored at zero, and because the level comes from that total a penalty can lower the level shown. Gems already earned are kept.

**Levels**

Reaching level 2 takes 100 XP and each later level takes 50 XP more than the one before (100, 150, 200, ...). The total XP at which level `n` is reached is `100(n-1) + 25(n-1)(n-2)`.

| Level | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
| ----- | - | - | - | - | - | - | - | - | - | -- |
| Total XP | 0 | 100 | 250 | 450 | 700 | 1000 | 1350 | 1750 | 2200 | 2700 |

Early levels arrive within days, which gives the new user a quick first reward; later ones take weeks. A user who reaches a 30 minute daily target earns about 40 XP a day (30 for the session, 10 for the bonus): level 2 in three days, level 5 in about two and a half weeks, level 10 in about ten weeks.

**Gems**

| Event | Gems | Setting | Once per |
| ----- | ---- | ------- | -------- |
| Reach a level (from level 2) | 5 | `gems.per-level` | level |
| Daily streak target reached | 1 | `gems.daily-streak` | day |
| Weekly streak target reached | 5 | `gems.weekly-streak` | week |

Gems are spent on streak freezes. A purchase is a negative ledger entry (`FREEZE_PURCHASE`, referenced to the `STREAK_FREEZE`), and it is refused if it would take the balance below zero.

**Streaks**

- A **daily streak** counts consecutive local calendar days on which the daily target was reached; a **weekly streak** counts consecutive Monday-to-Sunday weeks. A user with both configured has both.
- The current period counts as soon as it reaches its target. Until then the streak is the run that ended with the previous period, so today's streak is not lost while today is still open.
- A period with nothing qualifying recorded, or with the target missed, is a missed period, and it ends the streak: the count goes back to zero, unless streak freezes cover it.
- **Streak freezes** (daily only; the rules are in the requirements specification, section 13). Each freeze is a `streak_freezes` row with its purchase time; it is unused until `used_period_id` points at the day it covered. They are spent in `StreakService` when a daily period reaches its target: the missed days since the last completed or frozen day are counted, and if the unused freezes cover every one of them, oldest freeze first and only freezes bought before the day they cover ended, a period is recorded for each quiet day, marked `FROZEN`, and linked to its freeze. Otherwise nothing is spent. The streak count chains through `FROZEN` periods without counting them. While today's target is not reached yet, missed days the freezes could cover are treated the same way, but only reported (`dailyStreakProtectedDays`), never spent, since the gap may still grow.
- Periods are chained by their stored boundaries (each ends where the next begins), not recomputed from the time zone, so a change of time zone does not break a run.
- Only periods that reached their target count, under the configuration each was created with, so changing the target later does not rewrite history.

**Worked example.** A user with a 30 minute daily target completes a 30 minute session. The backend credits 30 minutes to today's period, which reaches its target, so it pays the streak rewards first (+10 XP, +1 gem). Completing the session then pays +30 XP. Total 40 XP, which is level 1 (the first level ends at 100), with 1 gem, and the daily streak is 1 day. Tomorrow they do the same: 80 XP, 2 gems, a 2 day streak. On the third day the 120 XP total crosses level 2, which also pays +5 gems.

**Where it lives.** `SessionService` calls `ProgressionService.awardSessionCompletion` when a session completes, after crediting the streak. `StreakService` calls `ProgressionService.awardStreakCompletion` when a period first reaches its target. `ProgressionService` pays the XP and gems, and then the gems for any level the award crossed, in the same transaction. The streak length is `StreakService.getCurrentStreakLength`.

**Not built.** Spending gems; XP and gem history endpoints; milestone bonuses for long streaks (for example 7 days); a "longest streak" record; showing the XP earned on the session summary.
