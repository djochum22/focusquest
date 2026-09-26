# FocusQuest

## Preliminary Requirements Specification

**Document status:** Working draft

**Purpose:** Consolidate the requirements and design decisions discussed so far for the FocusQuest productivity application.

**Initial platform:** Web application supported by a Chrome browser extension

**Initial storage model:** Local data storage with one local user account

**Primary implementation direction:** Vue.js and TypeScript frontend, Spring Boot backend, H2 database during development

## 1\. Product overview

FocusQuest is a productivity application inspired by the progression and habit-forming mechanics of Duolingo. Users create focus sessions, define the work they intend to complete, select distracting websites to block, and commit to a specific amount of focused work.

During an active focus session, selected domains and URL paths are blocked by a Chrome browser extension. Users can pause sessions, but blocking remains active. Daily and weekly streaks measure accumulated qualifying work over a defined period, while session rewards encourage users to complete a continuous focus commitment.

The initial version is intended to be a local-first productivity tool rather than a cloud-based social platform or an AI monitoring system.

### Core value proposition

The application helps users complete focused work by combining:

- Focus sessions with a defined duration.
- Blocking of distracting websites and URL paths.
- Daily and weekly time-based streaks.
- XP, levels, and later gems.
- Session history and progress statistics.
- Optional future computer-vision assistance.

### Central product distinction

The product has two related but separate progression concepts:

1. **Daily and weekly streaks measure accumulated qualifying work.**
2. **Focus sessions reward completing a continuous planned focus commitment.**

A user may contribute time to a streak without completing the session, but completion XP is awarded only for a completed session.

## 2\. Project goals

The MVP should:

- Help a user commit to a defined period of work.
- Block selected distractions during the commitment.
- Measure qualifying work across the day and week.
- Reward completed sessions with XP.
- Track streak progress transparently.
- Store all data locally for the initial single-user version.
- Provide a foundation that can later support accounts, synchronization, desktop blocking, computer vision, and social features.

### Long-term possibilities

Potential future directions include:

- Desktop-application blocking.
- Support for additional browsers.
- Multiple operating systems.
- Cloud synchronization.
- Computer-vision-assisted focus sessions.
- Social groups and accountability.
- More advanced quests, rewards, and skill categories.
- Mobile-device integrations.

## 3\. MVP scope

### Included in the MVP

- Web application.
- Chrome browser extension.
- One local user account.
- Local data storage.
- Focus sessions measured in minutes.
- Minimum session duration of 5 minutes.
- Free-text task description.
- User-selectable task type.
- Default task category suggestion.
- Task-required and task-free sessions.
- Domain blocking.
- URL-path blocking.
- User-defined allowlists.
- Pause and resume functionality.
- Website blocking during pauses.
- Daily streaks based on qualifying minutes.
- Weekly streaks based on qualifying minutes.
- Weekly period from Monday through Sunday.
- Session completion and abandonment states.
- Immutable completed sessions.
- XP for completed sessions.
- A daily XP penalty for manual overrides.
- Levels, and gems earned from levels and streaks.
- A current streak count: consecutive daily or weekly periods that reached their target. A missed period ends the streak.
- Local session history and progress data.

### Explicitly outside the initial MVP

- Computer vision.
- Phone detection.
- Automatic judgment of whether the user is productive.
- Cloud video processing.
- Facial recognition.
- Keystroke surveillance.
- Desktop application blocking.
- Mobile applications.
- Support for browsers other than Chrome.
- Multiple local or cloud users.
- Social groups and public leaderboards.
- Wearable integrations.
- AI-generated productivity scores.
- Employer or school monitoring.
- A guarantee that blocking cannot be bypassed.

## 4\. Users and stakeholders

### Primary MVP user

A single local user who wants to complete focused work and reduce distractions while studying, coding, writing, reading, or performing another selected task.

### Future stakeholders

- Users with synchronized accounts.
- Accountability partners.
- Researchers evaluating focus behavior.
- Administrators managing shared installations.

The MVP does not require separate roles or multi-user permissions.

## 5\. Platform and technical direction

### Frontend

- Vue.js.
- TypeScript.
- Vite.
- Vue Router if multiple views are required.
- Pinia if centralized state management becomes useful.
- Axios or the Fetch API.

### Backend

- Java.
- Spring Boot.
- Spring Web.
- Spring Data JPA.
- Spring Validation.
- Spring Security only if authentication is introduced, even though the first version has one local user.

### Database

- H2 for local development and prototyping.
- PostgreSQL later if the application becomes multi-user or is deployed publicly.
- Flyway or Liquibase should be considered from the beginning for schema migrations.

### Browser extension

- TypeScript.
- Chrome Extension Manifest V3.
- Declarative blocking rules where suitable.
- Minimal required browser permissions.
- Secure communication with the web application or local backend.

### Future desktop direction

If desktop-application blocking is added later, Electron with Vue and TypeScript is the most accessible initial option given the project's existing technologies. Tauri is another option but introduces Rust.

## 6\. Local-first architecture

The recommended MVP architecture is:

Vue.js web application

|

| REST or local communication

v

Local Spring Boot backend

|

v

H2 database

^

|

Chrome browser extension

The browser extension is responsible for blocking matching websites. The backend or local application is responsible for storing and validating session state, streak progress, XP, and other business rules.

### Recommended local backend

Although browser storage alone is possible, a local Spring Boot backend is recommended because it provides:

- A clear REST API.
- Centralized business rules.
- A data model close to the planned production architecture.
- Easier unit and integration testing.
- A straightforward migration path to PostgreSQL and multiple users.

### Local security requirements

Even though the backend is local, it should:

- Avoid exposing unrestricted endpoints.
- Use a local authentication token or equivalent mechanism.
- Restrict CORS to the expected frontend origin.
- Validate all requests.
- Avoid binding to all network interfaces unless required.
- Avoid logging task descriptions or other sensitive user data unnecessarily.

## 7\. Core concepts and terminology

### Focus session

A planned period of focused work with a required duration measured in minutes.

### Active focus time

Time during which a session is in the ACTIVE state. This contributes toward completing the planned session duration.

### Paused time

Time during which a session is in the PAUSED state. Website blocking remains active. The interval is not added to streak progress until the pause is finalized.

### Qualifying time

Time that contributes to daily or weekly streak progress. Under the current decisions, qualifying time includes active time and finalized paused time when the session matches the streak requirements.

### Session completion

A session is completed when its required active focus duration has been reached and it transitions to COMPLETED.

### Daily progress

The amount of qualifying work accumulated during the current local calendar day.

### Daily streak

The number of consecutive daily periods in which the user reaches the configured daily qualifying-minute target.

### Weekly streak

The number of consecutive Monday-to-Sunday periods in which the user reaches the configured weekly qualifying-minute target.

### Overtime

Qualifying time recorded after the current streak target has been reached. Overtime does not increase the current period's progress beyond its target and does not carry into another period.

### Manual override

An explicit action that releases website blocking before the normal release condition is satisfied. It applies a daily XP penalty and is permanently recorded.

### Streak freeze

Removed from scope. There are no streak freezes: a period that ends without its target being reached ends the streak.

### Gem

An in-app reward currency earned from levels and streak targets. See the technical architecture, section 19. Nothing can be bought with gems yet.

## 8\. Focus-session requirements

### Session creation

- The user shall be able to create a focus session.
- The user shall define the planned session duration in minutes.
- The minimum planned duration shall be 5 minutes.
- The system shall reject durations below 5 minutes.
- The user shall be able to enter a free-text task description.
- The user shall be able to select the type of task being completed.
- The system shall provide a default category based on the selected task type.
- The user shall be able to accept or change the suggested category before the session starts.
- The system shall support task-free sessions.
- The task type and category shall be fixed when the session starts.

### Initial task categories

The initial categories may include:

- Studying.
- Coding.
- Writing.
- Reading.
- Work.
- Planning.
- Creative work.
- Administration.
- Other.
- Task-free.

The exact category list can be refined during implementation.

### Session states

The session shall support the following states:

- PLANNED
- ACTIVE
- PAUSED
- COMPLETED
- ABANDONED
- INTERRUPTED

### Recommended transitions

- PLANNED -> ACTIVE
- ACTIVE -> PAUSED
- PAUSED -> ACTIVE
- ACTIVE -> COMPLETED
- ACTIVE -> ABANDONED
- PAUSED -> ABANDONED
- ACTIVE -> INTERRUPTED
- PAUSED -> INTERRUPTED
- INTERRUPTED -> ACTIVE (resume)
- INTERRUPTED -> ABANDONED

Completed sessions are terminal and cannot be edited.

### Session time model

The system should track:

- activeFocusTime
- pausedTime
- wallClockTime
- qualifyingTime
- overtime

Recommended definitions:

- activeFocusTime = time spent in ACTIVE state
- pausedTime = finalized time spent in PAUSED state
- wallClockTime = elapsed time since session start, excluding unresolved periods
- qualifyingTime = activeFocusTime + finalized pausedTime

For session completion:

- activeFocusTime >= plannedFocusTime

Paused time does not contribute to the required active focus duration.

### Completion

When a session completes:

- The session shall transition to COMPLETED.
- Any open pause must be finalized before completion, although the recommended interface requires the user to resume first.
- The session becomes immutable.
- Completion XP shall be awarded according to the XP rules.
- Completion XP shall be awarded only once.
- Qualifying time shall be added to the applicable streak period.

### Abandonment

When a session is abandoned:

- The session shall transition to ABANDONED.
- Any open pause shall be finalized.
- Qualifying time already recorded shall remain available for streak progress.
- No session-completion XP shall be awarded.
- The system shall evaluate whether website blocking can be released.

### Technical interruption

A technical interruption may occur because of:

- Application crash.
- Browser restart.
- Backend failure.
- Extension failure.
- Device shutdown.

The system shall recover the session safely and shall not silently award time that cannot be verified. Unfinalized paused time shall not be added until the user resumes or finalizes the session.

Decided detection: the Chrome extension checks in every 30 seconds, whether or not a session is running. A session is watched from the moment it starts or resumes if the extension checked in within the timeout before that, and otherwise from the extension's first check-in during the session. While a session is watched, a silence longer than a configurable timeout (3 minutes by default) interrupts it. The check is made on the next heartbeat, the next read of the current session, or the next session action, so a backend that was itself down is caught too. A session run without the extension is never interrupted, and neither is one started when the extension had not checked in recently (for example, after it was uninstalled).

When a session is interrupted:

- Time up to the extension's last check-in is kept, including a pause finalized at that moment, and is credited to the streak.
- The time after the last check-in cannot be verified and is dropped.
- Website blocking is released (`TECHNICAL_RELEASE`).
- The user can resume the session, which enforces blocking again, or abandon it, which leaves blocking released. Only the user's latest started session can be resumed; starting a new session supersedes an interrupted one.

## 9\. Pause behavior

When a user pauses an active session:

- The session enters PAUSED.
- Website blocking remains active.
- The pause start timestamp is recorded.
- Active focus time stops increasing.
- Session-completion progress stops increasing.
- XP is not awarded for paused time.
- Daily and weekly streak progress does not increase immediately.

### Pause finalization: A2 decision

Paused time is added to streak progress only when the pause ends or the session is finalized.

When the user resumes:

- The pause end timestamp is recorded.
- The pause duration is calculated.
- The finalized pause duration is added to qualifying time.
- The corresponding streak contribution is recorded. This credits the total time already passed in the session (the active time so far plus the pause), so the day's total is current while the session continues. When the session later ends, only the time since this credit is added.
- The session returns to ACTIVE.

When the user abandons or interrupts a paused session:

- The open pause is closed using the finalization timestamp.
- The finalized pause duration is added to qualifying time.
- The qualifying time is added to the relevant streak period.
- No session-completion XP is awarded.

If the application remains paused for several hours, no progress is awarded during that unresolved interval. Once the pause is finalized, the recorded duration is processed according to the time-period rules.

### Pause acceptance criteria

- Given an active session, pausing changes the state to PAUSED.
- A paused session continues blocking configured websites.
- While unresolved, a pause does not increase streak progress.
- While unresolved, a pause does not increase active focus duration.
- Resuming finalizes the pause duration.
- Finalized paused time is added to qualifying time.
- Abandoning while paused finalizes the open pause.
- A completed session cannot be edited.

## 10\. Website and URL blocking

The MVP shall support Chrome website blocking through a browser extension.

### Blocking targets

The user shall be able to block:

- Complete domains.
- URL paths.

Examples:

- youtube.com
- reddit.com
- youtube.com/shorts
- reddit.com/r/all

Domain rules should normally apply to relevant subdomains, such as:

- <www.youtube.com>
- m.youtube.com
- old.reddit.com

A path rule should apply to the path and its descendants. For example:

- youtube.com/shorts

should block:

- youtube.com/shorts
- youtube.com/shorts/example
- youtube.com/shorts?feature=share

### Allowlists

The user shall be able to add allowlist rules.

Recommended precedence:

1. Most-specific allowlist rule.
2. Most-specific block rule.
3. Broader allowlist rule.
4. Broader block rule.
5. Default allow.

Example:

- Block: example.com
- Allow: example.com/docs

Result:

- example.com/docs -> allowed
- example.com/docs/setup -> allowed
- example.com/forum -> blocked

This precedence must be documented and tested.

"Most specific" is defined as follows: a rule with more host labels is more specific (`m.youtube.com` over `youtube.com`); if the host is equally specific, the rule with more path segments is. Host specificity therefore outranks path specificity. Among all rules that match a URL the most specific wins, and when an allowlist rule and a block rule are equally specific the allowlist rule wins. The full matching and normalization behavior is specified in the technical architecture (section 9).

### Rule format

A rule is a domain (`example.com`) or a domain plus path (`example.com/docs`), with no scheme, port, query string, fragment or wildcard. Rules are stored lowercased and matching ignores case, including in the path, so a change of letter case cannot get around a rule. Query strings are rejected rather than ignored, because a rule for one specific page would otherwise silently become a rule for the whole section. A URL's own query string never affects matching.

### Blocking during a session

- Matching websites shall be blocked while a focus session is active.
- Matching websites shall remain blocked while the session is paused.
- The extension shall show a local blocked-page explanation.
- The page should display the blocked target, session name, remaining time, and relevant streak progress.
- The extension shall not provide a normal bypass button.
- Manual override is handled by the main application and is recorded as a penalty event.
- While blocking is being enforced, the blocking configuration may only get stricter: the user can add a block rule or remove an allowlist rule, but cannot edit or delete a block rule, or add or edit an allowlist rule. This replaces a per-session copy of the blocked targets; the user has one set of rules that applies to every session.

### Release behavior after abandonment

If the user abandons a session:

- If the daily streak target has already been reached, website blocking releases immediately.
- If the daily streak target has not been reached, website blocking remains active.
- The user may then use a manual override of the abandoned session to release blocking, subject to the daily XP penalty.

If the user completes a session, blocking is released. If a session is interrupted for a technical reason, blocking is released (`TECHNICAL_RELEASE`) so that a failure can never lock the user out. If an abandoned session keeps blocking active, it stays active until the user completes a session or overrides the abandoned session; starting a new session supersedes the abandoned one. Whether blocking is currently enforced is derived from the status of the user's most recently started session (see the technical architecture, section 13).

The session state and blocking state should be stored separately because an abandoned session can still have active website blocking.

Possible blocking states:

- ACTIVE
- RELEASED
- OVERRIDE_USED
- TECHNICAL_RELEASE

## 11\. Task types and streak qualification

The user can choose what type of task they are completing. The system should suggest a default category based on the selected task.

### Task modes

- TASK_REQUIRED
- TASK_FREE

### Task category behavior

- The user selects or confirms the task category before starting.
- The category is fixed when the session starts.
- Completed sessions cannot have their category changed.
- A task-free session has the category TASK_FREE.

### Streak qualification

By default:

- Task-free sessions do not count toward task-required streaks.
- Task-based sessions count only when their category matches the active streak requirements, if a category is required.
- Task-free sessions count when the user explicitly configures the streak to require task-free sessions.

The active streak configuration must define:

- period type
- required minutes
- task mode
- optional required category

### Default streak configuration

A user always has a streak configuration; it can never be missing. Until the user changes it, the default applies: a **daily** streak with a target of **30 minutes** that counts **task-based** sessions of any category. It is created when the account is set up (and on first use for any account that lacks one). Only the daily streak has a default; a weekly streak exists only when the user configures one.

## 12\. Daily and weekly streaks

### Daily streak

A daily streak requires a configurable total amount of qualifying minutes during a local calendar day.

Example:

- Daily target: 30 minutes

The user may meet the target through multiple qualifying sessions.

### Weekly streak

A weekly streak requires a configurable total amount of qualifying minutes from Monday through Sunday.

Example:

- Weekly target: 180 minutes

The application should use the user's local time zone when determining day and week boundaries.

### Streak configuration before a session

The user shall be able to configure the streak before starting a session.

The configuration may include:

- Daily or weekly period.
- Target amount of minutes.
- Task-required or task-free sessions.
- Optional required task category.

### Configuration changes

A new configuration applies immediately to future streak periods. A streak period that has already started continues using the configuration snapshot created for that period.

Example:

- Existing daily period: 30 minutes of studying
- New configuration: 45 minutes of coding

The existing daily period remains:

- 30 minutes of studying

The new configuration applies to the next applicable period.

### Streak-period snapshot

StreakPeriod

- id
- userId
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
- configurationSnapshotId
- completedAt

`qualifyingSeconds` and `overtimeSeconds` are tracked in seconds so that repeated small contributions (short sessions, finalized pauses) accumulate exactly instead of losing time to per-contribution minute rounding. `targetMinutes` remains a minute-granularity configuration value.

Possible statuses:

- ACTIVE
- COMPLETED
- MISSED
- FROZEN

### Daily streak progress

The daily streak measures overall qualifying work throughout the day. Qualifying time can come from multiple sessions and may remain valid even when a session is abandoned.

Example:

- Session 1: 15 qualifying minutes, abandoned
- Session 2: 10 qualifying minutes, completed
- Session 3: 5 qualifying minutes, completed
- Daily progress: 30 minutes

If the target is 30 minutes, the daily streak is completed. XP is awarded only for Sessions 2 and 3 if they were completed.

### Weekly streak progress

The weekly streak aggregates qualifying minutes from Monday through Sunday according to the active weekly configuration.

### Overtime

Once the current streak target has been reached:

- Progress is capped at the target.
- Additional qualifying time is recorded as overtime.
- Overtime does not carry into the next period.
- Overtime does not automatically award additional XP.
- Overtime is retained for total focus statistics or the overall progress measure.

The product should use the term Total Focus Time for cumulative time rather than overall streak where possible.

## 13\. Gems and streak loss

### Gems

Gems are an in-app reward currency. They are earned, not bought: 5 gems for each level reached (from level 2), 1 for reaching a daily streak target and 5 for a weekly one, each once. Every change is recorded as a `GemTransaction`. The amounts are configuration; see the technical architecture, section 19. Nothing can be bought with gems yet.

### Streak loss

There are no streak freezes. The current streak is the number of consecutive periods, ending with the current one, that reached their target. The current period counts once it reaches its target; until then the streak is the run up to the previous period. A period that ends without reaching its target, including one in which nothing qualifying was done, ends the streak, and the count returns to zero.

## 14\. Manual override and XP penalty

A manual override releases website blocking before the normal release condition is satisfied.

When the user activates a manual override:

- Website blocking releases immediately.
- It is allowed only for a session that has already been abandoned and is still holding blocking; a running (ACTIVE or PAUSED) session must be abandoned first, so the session is finished and its time credited before anything is overridden. The abandoned session is marked overrideUsed = true.
- No session-completion XP is awarded.
- A daily XP penalty is applied.
- The event is permanently recorded (currently as the overrideUsed flag, the OVERRIDE_USED blocking state and the penalty transaction; a dedicated BlockingOverride record with a reason is still planned).
- The user sees the penalty before confirming.

### Recommended penalty model

Represent the penalty as a separate negative XP transaction rather than silently rewriting old records.

ExperienceTransaction

- id
- userId
- amount
- type
- referenceId
- createdAt

Possible types:

- SESSION_COMPLETION
- STREAK_COMPLETION
- CHALLENGE_COMPLETION
- MANUAL_OVERRIDE_PENALTY

The exact penalty percentage or amount remains open. The implementation currently applies a fixed placeholder of 10 XP, once per overridden session, configurable through `focusquest.xp.manual-override-penalty`.

Example confirmation:

- Leaving this session will unblock the selected websites, but it will applyan XP penalty to today's progress. Do you want to continue?

## 15\. XP and progression

The initial product concept includes XP and levels inspired by gamified applications.

### Session XP

- Completed sessions receive completion XP.
- Abandoned sessions do not receive completion XP.
- Paused time does not generate separate XP.
- Overtime does not automatically generate additional XP.
- Duplicate XP awards must be prevented.

### XP events

The economy is defined in the technical architecture (section 19). The implemented events are:

- SESSION_COMPLETION: 1 XP per planned focus minute, once per completed session.
- STREAK_COMPLETION: +10 XP for a daily target, +50 XP for a weekly target, once per period.
- MANUAL_OVERRIDE_PENALTY: -10 XP, once per overridden session.

Levels follow a rising curve (level 2 at 100 XP, then 50 more XP per level). Challenge events are not implemented.

### XP history

The system should store XP transactions rather than only the current total. This supports:

- Auditing.
- Duplicate prevention.
- Corrections.
- Clear explanations to the user.

## 16\. Preliminary data model

### User

User

- id
- localIdentifier
- timezone
- createdAt

### FocusSession

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

### SessionPause

SessionPause

- id
- sessionId
- startedAt
- endedAt
- durationSeconds
- finalized

### BlockedTarget

BlockedTarget

- id
- userId
- targetType
- targetValue
- displayName
- active

### SessionBlockedTarget

SessionBlockedTarget

- id
- sessionId
- blockedTargetId

Not implemented. The user has a single set of block and allowlist rules that applies to every session, and the configuration lock (section 10) prevents loosening it while blocking is enforced.

### StreakConfiguration

StreakConfiguration

- id
- userId
- periodType
- targetMinutes
- requiredTaskMode
- requiredCategory
- effectiveFrom
- createdAt

### StreakPeriod

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

### StreakContribution

StreakContribution

- id
- streakPeriodId
- sessionId
- activeSeconds
- pausedSeconds
- qualifyingSeconds
- createdAt

### ExperienceTransaction

ExperienceTransaction

- id
- userId
- amount
- type
- referenceType
- referenceId
- createdAt

### GemTransaction

GemTransaction

- id
- userId
- amount
- transactionType
- referenceId
- createdAt

### BlockingOverride

BlockingOverride

- id
- sessionId
- reason
- xpPenalty
- createdAt

## 17\. Preliminary REST API

### Focus sessions

POST /api/focus-sessions

GET /api/focus-sessions/{id}

POST /api/focus-sessions/{id}/start

POST /api/focus-sessions/{id}/pause

POST /api/focus-sessions/{id}/resume

POST /api/focus-sessions/{id}/complete

POST /api/focus-sessions/{id}/abandon

POST /api/focus-sessions/{id}/override

GET /api/focus-sessions/current

GET /api/focus-sessions/planned

GET /api/focus-sessions/history

DELETE /api/focus-sessions/{id}

`GET /api/focus-sessions/current` returns the ACTIVE or PAUSED session, or the latest started session if it is INTERRUPTED, or 204 No Content. `GET /api/focus-sessions/planned` returns the session created but not started yet, or 204; there is at most one, since creating a session replaces an earlier unstarted one. `DELETE /api/focus-sessions/{id}` deletes a planned session and is refused for any other. `GET /api/focus-sessions/history` returns the ended sessions (completed, abandoned, interrupted), most recently started first, with an optional `limit` (default 50, at most 200). `GET /api/focus-sessions/{id}` is not implemented yet. `POST /api/focus-sessions/{id}/override` takes no body.

### Blocked targets

GET /api/blocked-targets

POST /api/blocked-targets

PUT /api/blocked-targets/{id}

DELETE /api/blocked-targets/{id}

### Allowlists

GET /api/allowlist-targets

POST /api/allowlist-targets

PUT /api/allowlist-targets/{id}

DELETE /api/allowlist-targets/{id}

### Streaks

GET /api/streaks/current

GET /api/streaks/history

GET /api/streak-configurations

POST /api/streak-configurations

PUT /api/streak-configurations/{id}

GET /api/streak-periods/current

`GET /api/streaks/current`, `GET` and `POST /api/streak-configurations` and `PUT /api/streak-configurations/{id}` are implemented; see the technical architecture (section 10) for their shapes and rules. `GET /api/streaks/history` and `GET /api/streak-periods/current` are not implemented yet.

### Chrome extension

GET /api/extension/blocking-state

GET /api/extension/current-session

POST /api/extension/heartbeat

The extension polls these with the same bearer token as the rest of the API; see the technical architecture (section 10) for the response shapes.

### Errors

All errors return `{ "code": "...", "message": "..." }`; see the technical architecture (section 7) for the codes.

### Progression and rewards

GET /api/me/progression

GET /api/me/xp-history

GET /api/me/gems

GET /api/me/gem-history

GET /api/me/streak-freezes

POST /api/me/streak-freezes/purchase

Only `GET /api/me/progression` is implemented (XP total, level, progress through the level, and gem balance). The gem, freeze and history endpoints wait for the gem economy and Phase 5.

### Settings

PUT /api/me/profile

GET /api/export

POST /api/me/data/restore

DELETE /api/me/data

`PUT /api/me/profile` changes the display name and time zone. A new time zone takes effect from the next daily and weekly period, and changing it is refused while website blocking is active. `GET /api/export` returns all of the caller's data as JSON, complete enough to be a backup. `POST /api/me/data/restore` replaces all of it with such a backup, keeping the account's sign-in, and is refused while website blocking is active. `DELETE /api/me/data` deletes all of it, including the account, and is refused while website blocking is active. See the technical architecture (section 10).

## 18\. Acceptance-criteria themes

Detailed user stories and acceptance criteria will be created next. They should cover at least:

1. Creating a focus session.
2. Selecting a task type.
3. Accepting or changing a suggested category.
4. Configuring a daily streak.
5. Configuring a weekly streak.
6. Starting a session.
7. Blocking a domain.
8. Blocking a URL path.
9. Applying an allowlist rule.
10. Pausing a session.
11. Resuming a session.
12. Finalizing paused time.
13. Completing a session.
14. Abandoning a session.
15. Releasing blocking after daily completion.
16. Keeping blocking after abandonment before daily completion.
17. Activating a manual override.
18. Applying an XP penalty.
19. Accumulating daily qualifying minutes.
20. Accumulating weekly qualifying minutes.
21. Handling overtime.
22. Completing a streak period.
23. Ending the streak when a period is missed.
24. Preserving an immutable completed session.
25. Recovering after application or browser restart.
26. Preventing duplicate XP, gem, or streak contributions.

## 19\. Non-functional requirements

### Privacy

- The MVP shall store data locally.
- The MVP shall not use computer vision.
- The MVP shall not store camera footage.
- The application should not collect browsing history beyond what is needed to enforce selected blocking rules.
- The application should request only the browser permissions required for website blocking.

### Reliability

- The application shall recover safely after restart.
- The system shall not award unverified paused time while the application is closed.
- Session transitions shall be validated by the backend or local business-logic layer.
- Operations that award XP, gems, or streak contributions shall be idempotent.

### Usability

- The user should be able to start a session with minimal navigation.
- The active-session screen should clearly show task, remaining time, session state, streak progress, and blocking state.
- The block page should clearly explain why access is unavailable.
- The user should receive clear warnings before a manual override.
- Time values should be displayed in minutes while being stored with sufficient precision internally.

### Maintainability

- Business rules should be implemented in services rather than controllers or UI components.
- Matching or blocking rules should be independently testable.
- Database migrations should be versioned.
- External or browser-specific functionality should be isolated behind interfaces where practical.

### Compatibility

- Google Chrome is the first supported browser.
- The initial version is a web application supported by a Chrome extension.
- Other browsers and desktop operating systems are future scope.

## 20\. Development roadmap

### Phase 1: Requirements and domain rules

- Finalize terminology.
- Finalize session state transitions.
- Finalize daily and weekly streak rules.
- Define qualifying-time behavior.
- Define blocking-release rules.
- Define the first XP events.
- Define open questions and assumptions.

### Phase 2: Project foundation

- Create Spring Boot backend.
- Create Vue.js frontend.
- Configure H2.
- Configure database migrations.
- Create the Chrome extension project.
- Set up Git and GitHub.
- Add initial automated tests.

### Phase 3: Session management

- Implement session creation.
- Implement task types and categories.
- Implement start, pause, resume, complete, abandon, and interrupt operations.
- Implement session recovery.
- Implement immutable completed sessions.

### Phase 4: Streak management

- Implement daily periods.
- Implement Monday-to-Sunday weekly periods.
- Implement configuration snapshots.
- Implement qualifying-time aggregation.
- Implement overtime handling.
- Implement streak completion and missed-period handling.

### Phase 5: Chrome blocking

- Implement block rules.
- Implement path matching.
- Implement allowlist precedence.
- Implement blocked-page UI.
- Connect extension state with the current session.
- Handle extension restart and backend failure.

### Phase 6: XP and gems

- Implement XP transactions.
- Award completion XP.
- Implement manual-override penalties.
- Add progress display.
- Define and implement gem rewards.

### Phase 7: History and dashboard

- Display session history.
- Display daily and weekly progress.
- Display total focus time.
- Display XP, levels, and gems.
- Add clear explanations of streak contributions.

### Phase 8: Testing and hardening

- Unit-test time and streak calculations.
- Test all state transitions.
- Test blocking-rule precedence.
- Test restart and crash recovery.
- Test duplicate requests.
- Test local data backup and restoration.
- Test Chrome extension behavior.

## 21\. Potential future computer-vision feature

Computer vision is not part of the MVP. It may later become a thesis or experimental feature.

### Possible research direction

**Privacy-preserving computer vision for detecting interruptions during focused computer work.**

Possible research question:

How accurately can a local computer-vision system detect user absence or smartphone usage during a focus session without storing video data?

### Possible future detections

- User present or absent.
- Face approximately directed toward the screen.
- Phone-like object visible.
- Possible interruption intervals.

### Privacy principles

- Opt-in only.
- Local processing.
- No raw video upload.
- No video storage by default.
- No facial recognition.
- Store only event types and confidence values.
- Provide clear consent and controls.

The research should define measurable observable events rather than claiming to identify whether someone is truly productive.

## 22\. Open decisions

The following decisions remain open or need more precision:

1. Exact XP values for completing sessions.
2. Exact daily XP penalty for manual overrides. (A placeholder of 10 XP is implemented.)
3. Exact gem rewards derived from XP.
4. What gems can be spent on.
5. Whether an abandoned session's websites remain blocked until a new session is completed or until another qualifying condition is reached. (Provisionally decided: they remain blocked until the daily target is reached, a session is completed, or the user overrides.)
6. The exact behavior when a session crosses midnight or the end of a weekly period. (Decided: its time is split at the boundary, and each part counts toward the period it was spent in.)
7. Whether weekly progress includes time that also completed a daily streak.
8. Whether a paused interval spanning midnight is split across periods when finalized. (Decided: yes, the same way as active time.)
9. Whether completion requires active focus time only, as currently recommended, or elapsed session time.
10. Whether the user can manually end a completed-period session without a penalty.
11. Whether the MVP supports an emergency release distinct from a manual override.
12. Exact Chrome extension-to-application communication method. (Decided: the extension polls authenticated REST endpoints, with a lightweight heartbeat that signals when to re-fetch.)
13. Exact local authentication mechanism.
14. Data backup and export format. (Decided: the JSON export is the backup. From format 2.0 it holds every stored row and can be restored from Settings; a restore replaces all current data.)
15. Exact initial task-category list.

## 23\. Current product definition

FocusQuest is a local-first productivity web application supported by a Chrome browser extension. A single local user can create focus sessions with durations of at least five minutes, define a task and category, and block selected domains and URL paths. Users may add allowlist rules. Websites remain blocked during paused sessions.

Daily and weekly streaks measure accumulated qualifying work. Daily periods use the user's local calendar day. Weekly periods run from Monday through Sunday. Qualifying time can be accumulated across multiple sessions. Paused time is counted only when the pause is finalized by resuming, abandoning, interrupting, or otherwise closing the session. An abandoned session can contribute qualifying time but awards no session-completion XP.

A completed session rewards continued focused work. Completion XP is awarded only when the required active focus duration has been reached and the session is completed. Completed sessions are immutable. Overtime is recorded but does not increase current streak progress, carry into another period, or automatically generate additional XP.

If an abandoned session occurs after the daily streak has already been reached, websites unblock immediately. If the daily streak has not been reached, blocking remains active. A manual override can release blocking but applies a daily XP penalty and is permanently recorded. Gems are earned from levels and streak targets; nothing can be bought with them yet. There are no streak freezes: a period that ends without reaching its target ends the streak.

The next project step is to transform this preliminary specification into user stories and detailed acceptance criteria.