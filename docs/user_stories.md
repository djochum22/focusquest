## **Epic 1: Local user and application setup**

## **US-001: First-launch setup**

As a user,  
I want to configure a local profile on first launch,  
so that the application can store my data locally and display my name.

**Acceptance criteria:**

- Given the application is launched for the first time,  
  when the setup screen is displayed,  
  then the user can enter a username, a password, a display name and confirm the time zone.
- Given the user has entered all required fields,  
  when the user confirms the setup,  
  then the application stores the local profile and does not show the setup screen again.
- Given the user has left the display name, username or password empty, or the password confirmation does not match,  
  when the user confirms the setup,  
  then the application shows what is missing and does not create the profile.
- Given the user confirms the setup,  
  when the local profile is created,  
  then the application also creates the default daily streak configuration (30 minutes, task-based sessions).
- Given no local profile exists yet,  
  when the user opens the application,  
  then the application shows the setup screen instead of the login screen.
- Given a local profile already exists,  
  when the user opens the setup screen,  
  then the application redirects to the login screen.
- Given the user confirms the setup,  
  when the local profile is created,  
  then the user is signed in and taken to the dashboard without a separate login step.

## **US-002: View and edit local profile**

As a user,  
I want to view and edit my local profile,  
so that I can change my display name and time zone.

**Acceptance criteria:**

- Given the user has completed setup,  
  when the user opens the profile settings,  
  then the application displays the current display name and time zone.
- Given the user is in profile settings,  
  when the user changes the display name or time zone and saves,  
  then the application persists the changes and uses the new time zone for day and week boundaries.
- Given the user changes the time zone,  
  when the change is saved,  
  then existing session timestamps remain unchanged, but future period calculations use the new time zone.

## **US-003: Export local data**

As a user,  
I want to export all local data as JSON,  
so that I can back up my sessions and progress.

**Acceptance criteria:**

- Given the user is in settings,  
  when the user selects "Export data,"  
  then the application generates a JSON file containing sessions, streaks, XP, gems, and configuration.
- Given the export is requested,  
  when the file is generated,  
  then the user can download the file to their device.

## **US-004: Delete all local data**

As a user,  
I want to delete all local data,  
so that I can reset the application to a clean state.

**Acceptance criteria:**

- Given the user is in settings,  
  when the user selects "Delete all data,"  
  then the application requires an explicit second confirmation.
- Given the user confirms deletion,  
  when the deletion is performed,  
  then all local sessions, streaks, XP, gems, and configuration are removed.
- Given the deletion is complete,  
  when the user navigates to the application,  
  then the application behaves as if it were launched for the first time.

## **Epic 2: Task types and categories**

## **US-010: Select task type**

As a user,  
I want to select the type of task I am completing,  
so that the application can suggest an appropriate category.

**Acceptance criteria:**

- Given the user is creating a session,  
  when the user selects a task type,  
  then the application suggests a default category based on the selected type.
- Given the user has selected a task type,  
  when the user accepts the suggested category,  
  then the session uses that category.
- Given the user has selected a task type,  
  when the user changes the category,  
  then the session uses the user-selected category.

## **US-011: Task-free session**

As a user,  
I want to create a task-free session,  
so that I can track focus time without specifying a task.

**Acceptance criteria:**

- Given the user is creating a session,  
  when the user selects "Task-free,"  
  then the session is marked as task-free and has no task description.
- Given a task-free session,  
  when the streak requires task-based sessions,  
  then the session does not count toward the streak.
- Given a task-free session,  
  when the streak explicitly requires task-free sessions,  
  then the session counts toward the streak.

## **Epic 3: Focus-session creation**

## **US-020: Create a focus session**

As a user,  
I want to create a focus session with a duration and task,  
so that I can commit to focused work.

**Acceptance criteria:**

- Given the user is on the session-creation screen,  
  when the user enters a duration in minutes,  
  then the application rejects durations below 5 minutes.
- Given the user enters a valid duration,  
  when the user enters a task description,  
  then the session stores the task description.
- Given the user selects a task type,  
  when the session is created,  
  then the application suggests a default category.
- Given the user confirms the session,  
  when the session is created,  
  then the session is in the PLANNED state.

## **US-021: Configure blocked targets for a session**

As a user,  
I want to select which domains and paths to block during a session,  
so that I can prevent access to distracting websites.

**Acceptance criteria:**

- Given the user has added block rules for domains and paths,  
  when a session starts,  
  then the Chrome extension blocks the active rules.
- Given a session is active or paused, or blocking is still enforced after an abandonment,  
  when the user adds a block rule or removes an allowlist rule,  
  then the application accepts the change, because it can only make blocking stricter.
- Given a session is active or paused, or blocking is still enforced after an abandonment,  
  when the user attempts to edit or delete a block rule, or to add or edit an allowlist rule,  
  then the application refuses the change with a clear message.

## **Epic 4: Starting and completing sessions**

## **US-030: Start a focus session**

As a user,  
I want to start a planned focus session,  
so that blocking becomes active and my focus time begins.

**Acceptance criteria:**

- Given a session is in the PLANNED state,  
  when the user starts the session,  
  then the session transitions to ACTIVE.
- Given the session starts,  
  when the session becomes active,  
  then the Chrome extension activates blocking for the configured targets.
- Given the session is active,  
  when time passes,  
  then the application tracks active focus time separately from paused time.

## **US-031: Complete a focus session**

As a user,  
I want to complete a focus session after reaching the planned duration,  
so that I receive session-completion XP and the session is recorded as completed.

**Acceptance criteria:**

- Given a session is in the ACTIVE state,  
  when the user has accumulated active focus time equal to or greater than the planned focus minutes,  
  then the user can complete the session.
- Given the session is completed,  
  when the completion is recorded,  
  then the session transitions to COMPLETED.
- Given the session is completed,  
  when the session is finalized,  
  then the session becomes immutable.
- Given the session is completed,  
  when completion XP is awarded,  
  then the XP is recorded as a transaction and cannot be awarded again for the same session.
- Given the session is completed,  
  when the session's qualifying time is calculated,  
  then the time is added to the applicable streak period.

## **Epic 5: Pausing and resuming sessions**

## **US-040: Pause an active session**

As a user,  
I want to pause an active session,  
so that I can take a break while keeping websites blocked.

**Acceptance criteria:**

- Given a session is in the ACTIVE state,  
  when the user pauses the session,  
  then the session transitions to PAUSED.
- Given the session is paused,  
  when the pause is recorded,  
  then the application stores the pause start timestamp.
- Given the session is paused,  
  when the Chrome extension is active,  
  then the extension continues blocking the configured targets.
- Given the session is paused,  
  when the pause is unresolved,  
  then the application does not add the paused time to streak progress.

## **US-041: Resume a paused session**

As a user,  
I want to resume a paused session,  
so that I can continue my focus commitment.

**Acceptance criteria:**

- Given a session is in the PAUSED state,  
  when the user resumes the session,  
  then the application records the pause end timestamp.
- Given the pause is finalized,  
  when the pause duration is calculated,  
  then the finalized paused time is added to the session's qualifying time.
- Given the pause is finalized,  
  when the streak contribution is updated,  
  then the total time already passed in the session (active time so far plus the pause) is added to the applicable daily and weekly streak periods.
- Given time was credited to the streak at resume,  
  when the session later ends,  
  then only the time since that credit is added, so no time is counted twice.
- Given the session is resumed,  
  when the resume is recorded,  
  then the session transitions back to ACTIVE.

## **Epic 6: Abandoning sessions and manual overrides**

## **US-050: Abandon an active or paused session**

As a user,  
I want to abandon a session,  
so that I can stop the commitment before completion.

**Acceptance criteria:**

- Given a session is in the ACTIVE or PAUSED state,  
  when the user abandons the session,  
  then the session transitions to ABANDONED.
- Given the session is abandoned,  
  when any open pause exists,  
  then the application finalizes the pause and calculates its duration.
- Given the session is abandoned,  
  when qualifying time is calculated,  
  then the active and finalized paused time not already credited at a resume is added to the applicable streak period.
- Given the session is abandoned,  
  when session-completion XP is considered,  
  then no completion XP is awarded.

## **US-051: Release blocking after daily streak completion**

As a user,  
I want websites to unblock immediately when I abandon a session after completing my daily streak,  
so that I can use my browser normally.

**Acceptance criteria:**

- Given the daily streak target has already been reached,  
  when the user abandons a session,  
  then the application releases website blocking immediately.
- Given the daily streak has not been reached,  
  when the user abandons a session,  
  then the application keeps website blocking active.

## **US-052: Manual override**

As a user,  
I want to manually override blocking,  
so that I can unblock websites even if my daily streak is incomplete.

**Acceptance criteria:**

- Given a session is active or paused,  
  when the user looks for a manual override,  
  then no override is offered, and the application rejects an override request until the session has been abandoned.
- Given a session has been abandoned and website blocking is still active,  
  when the user activates a manual override,  
  then the application displays a warning that a daily XP penalty will be applied.
- Given the user confirms the override,  
  when the override is executed,  
  then website blocking is released immediately.
- Given the override is executed,  
  when the override is recorded,  
  then the already-abandoned session is marked with overrideUsed = true.
- Given the override is executed,  
  when XP is calculated for the day,  
  then a daily XP penalty is applied, once for that session.
- Given the override is executed,  
  when the event is recorded,  
  then the override and penalty are stored permanently.

## **Epic 7: Domain and URL-path blocking**

## **US-060: Block a domain**

As a user,  
I want to block a complete domain during a session,  
so that I cannot access distracting websites on that domain.

**Acceptance criteria:**

- Given the user creates a block rule for a domain,  
  when the session starts,  
  then the Chrome extension blocks the domain and relevant subdomains.
- Given a domain rule is active,  
  when the user visits <www.example.com> or example.com,  
  then the extension blocks access and shows the blocked page.

## **US-061: Block a URL path**

As a user,  
I want to block a specific URL path,  
so that I can block part of a website while allowing other parts.

**Acceptance criteria:**

- Given the user creates a block rule for example.com/path,  
  when the session is active,  
  then the extension blocks example.com/path and all child paths.
- Given a path rule is active,  
  when the user visits example.com/path or example.com/path/subpage,  
  then the extension blocks access.
- Given a path rule is active,  
  when the user visits example.com/other,  
  then the extension allows access unless another rule blocks it.

## **Epic 8: Allowlists**

## **US-070: Add an allowlist rule**

As a user,  
I want to add an allowlist rule,  
so that I can permit specific websites or paths even when a broader block rule is active.

**Acceptance criteria:**

- Given the user creates an allowlist rule,  
  when the session is active,  
  then the extension evaluates allowlist rules before block rules.
- Given an allowlist rule for example.com/docs,  
  when a broader block rule exists for example.com,  
  then example.com/docs is allowed while other paths on example.com remain blocked.

## **US-071: Apply allowlist precedence**

As a user,  
I want the most-specific rule to take priority,  
so that my allowlist and block rules behave predictably.

**Acceptance criteria:**

- Given conflicting rules exist,  
  when a URL is evaluated,  
  then the extension applies the most-specific matching rule first.
- Given an allowlist and block rule match the same URL,  
  when the allowlist rule is more specific,  
  then the URL is allowed.
- Given an allowlist and block rule match the same URL,  
  when the block rule is more specific,  
  then the URL is blocked.

## **Epic 9: Daily and weekly streaks**

## **US-080: Configure a daily streak**

As a user,  
I want to configure a daily streak,  
so that I can track my focus progress each day.

**Acceptance criteria:**

- Given the user has never configured a streak,  
  when the application needs a streak configuration,  
  then it uses the default: daily, 30 minutes, task-based sessions of any category, and a user never has no streak configuration.
- Given the user configures a streak,  
  when the user selects "Daily,"  
  then the application sets the period type to daily.
- Given the user sets a daily target,  
  when the target is saved,  
  then the application enforces a minimum target of 5 minutes.
- Given a daily streak is active,  
  when the user completes qualifying sessions,  
  then the application aggregates qualifying minutes for the current local day.

## **US-081: Configure a weekly streak**

As a user,  
I want to configure a weekly streak,  
so that I can track my focus progress from Monday to Sunday.

**Acceptance criteria:**

- Given the user configures a streak,  
  when the user selects "Weekly,"  
  then the application sets the period type to weekly.
- Given a weekly streak is active,  
  when the user completes qualifying sessions,  
  then the application aggregates qualifying minutes for the current Monday-to-Sunday period.

## **US-082: Switch from daily to weekly streak**

As a user,  
I want to switch from a daily to a weekly streak,  
so that I can change my focus goal type.

**Acceptance criteria:**

- Given the user has an active daily streak,  
  when the user switches to a weekly streak,  
  then the daily streak count carries over as a historical maximum.
- Given the user switches to weekly,  
  when the switch is performed,  
  then the current daily period is considered completed if its target was already reached; otherwise it is archived as missed.
- Given the switch is performed,  
  when the weekly streak starts,  
  then a new weekly period begins according to the current week.

## **US-083: Switch from weekly to daily streak**

As a user,  
I want to switch from a weekly to a daily streak,  
so that I can focus on daily goals.

**Acceptance criteria:**

- Given the user has an active weekly streak,  
  when the user switches to a daily streak,  
  then the weekly streak is archived.
- Given the switch is performed,  
  when the daily streak starts,  
  then a new daily streak series begins at zero.

## **Epic 10: Streak configuration**

## **US-090: Configure task requirements for a streak**

As a user,  
I want to specify whether my streak requires task-based or task-free sessions,  
so that my streak reflects the type of work I want to complete.

**Acceptance criteria:**

- Given the user configures a streak,  
  when the user selects "Task required,"  
  then only task-based sessions can count toward the streak.
- Given the user selects "Task-free only,"  
  when the streak is active,  
  then only task-free sessions count toward the streak.

## **US-091: Configure a required task category**

As a user,  
I want to require a specific task category for my streak,  
so that my streak reflects a particular type of work.

**Acceptance criteria:**

- Given the user configures a streak,  
  when the user selects a required category,  
  then only sessions with that category count toward the streak.
- Given the user selects "Any task category,"  
  when the streak is active,  
  then any task-based session counts if other requirements are met.

## **US-092: Apply configuration to future periods only**

As a user,  
I want changes to my streak configuration to apply only to future periods,  
so that my current period is not affected mid-way.

**Acceptance criteria:**

- Given a streak period has already started,  
  when the user changes the streak configuration,  
  then the current period continues using its original configuration.
- Given the configuration is changed,  
  when a new period starts,  
  then the new period uses the updated configuration.

## **Epic 11: XP and penalties**

## **US-100: Award XP for completed sessions**

As a user,  
I want to receive XP when I complete a session,  
so that my focus work is rewarded.

**Acceptance criteria:**

- Given a session is completed,  
  when the completion is recorded,  
  then the application awards session-completion XP.
- Given XP is awarded,  
  when the transaction is recorded,  
  then the same session cannot award completion XP again.

## **US-101: Apply daily XP penalty for manual override**

As a user,  
I want my manual override to apply a daily XP penalty,  
so that I am discouraged from abandoning sessions early.

**Acceptance criteria:**

- Given the user activates a manual override,  
  when the override is confirmed,  
  then the application applies a daily XP penalty.
- Given the penalty is applied,  
  when the daily XP total is calculated,  
  then the penalty is recorded as a separate transaction.
- Given the penalty would reduce daily XP below zero,  
  when the calculation is performed,  
  then the daily XP total has a minimum of zero.

## **Epic 12: Gems and streak freezes**

## **US-110: Earn gems based on XP**

As a user,  
I want to earn gems based on my XP,  
so that I can use them for rewards such as streak freezes.

**Acceptance criteria:**

- Given the user earns XP,  
  when the XP is recorded,  
  then the application calculates gem rewards according to the configured rules.
- Given gems are earned,  
  when the gem transaction is recorded,  
  then the user's gem balance is updated.

## **US-111: Purchase a streak freeze**

As a user,  
I want to purchase a streak freeze with gems,  
so that I can protect my streak if I miss a period.

**Acceptance criteria:**

- Given the user has sufficient gems,  
  when the user purchases a streak freeze,  
  then the application deducts the configured gem cost.
- Given the purchase is successful,  
  when the transaction is recorded,  
  then the user's available freeze count increases.

## **US-112: Automatic consumption of a streak freeze**

As a user,  
I want my streak freeze to be automatically consumed when I miss a streak period,  
so that my streak is preserved.

**Acceptance criteria:**

- Given a streak period ends without reaching its target,  
  when the user has at least one available freeze,  
  then the application consumes one freeze automatically.
- Given a freeze is consumed,  
  when the streak is evaluated,  
  then the streak is preserved and the period is marked as frozen.
- Given the user has no available freezes,  
  when the period ends without reaching the target,  
  then the streak is lost according to the streak policy.

## **Epic 13: History and statistics**

## **US-120: View session history**

As a user,  
I want to view my session history,  
so that I can see my completed and abandoned sessions.

**Acceptance criteria:**

- Given the user opens the history view,  
  when the sessions are loaded,  
  then the application displays completed and abandoned sessions with their task, duration, and status.
- Given a session is completed,  
  when the user views the session,  
  then the session details are immutable.

## **US-121: View streak progress**

As a user,  
I want to view my current streak progress,  
so that I know how close I am to completing my daily or weekly target.

**Acceptance criteria:**

- Given the user views the dashboard,  
  when the current streak period is loaded,  
  then the application displays the target minutes and the current qualifying minutes.
- Given the streak period is completed,  
  when the user views the streak,  
  then the application displays the completed status and streak count.

## **Epic 14: Recovery and local persistence**

## **US-130: Recover after restart during an active session**

As a user,  
I want the application to recover safely after a restart during an active session,  
so that my session state is consistent.

**Acceptance criteria:**

- Given the application or browser restarts during an active session,  
  when the application restarts,  
  then the session is restored in the INTERRUPTED state unless the system can reliably determine that it remained enforced.
- Given the session is interrupted,  
  when the user resumes or abandons it,  
  then the application finalizes any open intervals and updates streak contributions accordingly.

## **US-131: Preserve data across restarts**

As a user,  
I want my sessions and streaks to persist across application restarts,  
so that I do not lose my progress.

**Acceptance criteria:**

- Given the user has completed sessions and streaks,  
  when the application is closed and reopened,  
  then all sessions, streaks, XP, and gems are restored.
- Given the data is restored,  
  when the user views their history,  
  then the data matches the state before the restart.

## **Epic 15: Chrome-extension communication**

## **US-140: Activate blocking when a session starts**

As a user,  
I want the Chrome extension to activate blocking when a session starts,  
so that my selected websites are inaccessible during the session.

**Acceptance criteria:**

- Given a session starts,  
  when the backend or local service notifies the extension,  
  then the extension activates blocking for the configured targets.
- Given the extension is active,  
  when the user visits a blocked URL,  
  then the extension displays the blocked page.

## **US-141: Release blocking when conditions are met**

As a user,  
I want the extension to release blocking when the session is completed or when the daily streak is complete and the session is abandoned,  
so that I can use my browser normally.

**Acceptance criteria:**

- Given a session is completed,  
  when the completion is recorded,  
  then the extension releases blocking for that session's targets.
- Given the daily streak target has been reached,  
  when the user abandons a session,  
  then the extension releases blocking immediately.
- Given the daily streak target has not been reached,  
  when the user abandons a session without override,  
  then the extension continues blocking.
- Given a session is interrupted for a technical reason,  
  when the interruption is recorded,  
  then the extension releases blocking so the user is never locked out by a failure.

## **US-142: Handle extension restart**

As a user,  
I want the extension to reconnect and restore the blocking state after a restart,  
so that my session remains enforced.

**Acceptance criteria:**

- Given the extension restarts during an active session,  
  when the extension reconnects,  
  then it restores the blocking state automatically.
- Given the extension reconnects,  
  when the session is still active,  
  then the extension continues blocking the configured targets.