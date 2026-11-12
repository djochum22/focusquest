# Handoff: what to do next (12 November 2026)

Start here. The details live in two longer notes:
- `docs/platform_roadmap.md`: the container, every browser, the desktop app
- `docs/camera_roadmap.md`: camera verification

## Security pass: done, committed and pushed (12 November 2026)

A read of every part (backend, web app, extension, desktop app, camera companion) for security risks, done 11 November 2026. Four were found and fixed. Before pushing on 12 November 2026 every suite passed together with the redesign below, the end-to-end suite included (`FOCUSQUEST_E2E=1 ./scripts/test-all.sh`), and the built jar had been walked through in a headless browser with no policy violations.

**Fixed:**

1. **The database was reachable from the network.** `AUTO_SERVER=TRUE` in the database address made H2 open a server on a random port on every interface, behind the empty password. Removed (`application.yml`); the backend now listens on its own port only.
2. **Passwords could be guessed without limit.** After 5 failed sign-ins in a row, sign-in locks for 30 seconds, doubling up to 15 minutes, answering 429 (`LoginThrottle`, US-164).
3. **No content security policy.** Responses now carry one, and `Referrer-Policy: same-origin` (`SecurityConfig`). The theme script moved out of `index.html` into `frontend/public/theme-init.js`, since inline scripts no longer run.
4. **Two setups at once could create two accounts.** Setup now runs one at a time (`AuthService.setup`).
5. **Packages with known advisories** in `frontend/` and `extension/`: updated with `npm audit fix` (lock files only).

**Still to try by hand** (none of these could be tried in a test):

- [ ] Open the desktop app against the new backend (rebuild it with `npm run build` in `desktop/`) and check Settings still shows it connected and the app list loads on the Blocking page. The policy allows the app's channel (`ipc:`), but this could not be tried here.
- [ ] With the extension installed, check Settings shows it connected and a blocked site still goes to the blocked page.
- [ ] Sign in wrongly five times and check the page says how long to wait.
- [ ] If anything opened the database from outside while the backend ran (a database tool), use the H2 console instead (`FOCUSQUEST_H2_CONSOLE=true`).

**Left as they are, on purpose:**

- The remaining `npm audit` findings (5 moderate in `frontend/`, 3 low in `extension/`) are in test and packaging tools only, and fixing them means downgrading `@vue/test-utils` or `web-ext`.
- The extension and the backend's CORS list trust `http://localhost:5173`, the dev server's address, in every build. Any other project run on that port could disconnect the extension. Dropping it from release builds would need a separate dev build.
- The desktop app trusts whatever answers as FocusQuest on 127.0.0.1:8080, and its own start page has no content security policy (`"csp": null` in `tauri.conf.json`); that page shows text only.
- Rust and Python packages were not checked for advisories: `cargo-audit` and `pip-audit` are not installed.

## Redesign: done, committed and pushed (12 November 2026)

The whole web app was restyled on 11 November 2026 at the user's request: "take inspiration from Apple, something new and fresh and especially decluttered". The user looked it over and asked for it to be pushed on 12 November 2026. The design system is in `DESIGN.md` (with its machine-readable copy in `.impeccable/design.json`); the brief and its direction are in `.impeccable/surfaces/`.

**What changed:**

1. **Look.** The system typeface (San Francisco on a Mac) replaces Young Serif and Atkinson Hyperlegible, which are no longer bundled. A white page (black in the dark) replaces the buff one. Fields, tracks and quiet buttons are soft grey fills with no outlines; the only lines are hairlines between rows. Every button is a pill. One green for acting and a session's progress, one blue for today's target, orange for the flame.
2. **Header.** A thin translucent bar with the brand and five pages: Focus, Streaks, History, Blocking, Settings. The theme switch, the user's name and Sign out left it: **Sign out** is at the foot of Settings, Account, and **Appearance** (Automatic, Light, Dark) is its own row in Settings.
3. **Start page.** The greeting is the title ("Ready to focus?" is gone). Below it one line (today's ring, minutes, streak), then two controls instead of five: one field holding the task and the category, and one row of lengths. **Task-free is the last entry of the category menu** (it replaces the "Working on a task / Task-free" switch) and **Stopwatch is the last length** (it replaces the "Timer / Stopwatch" switch). The length that finishes today's target carries a small blue ring instead of a line of text below. "Continue where you left off" is a row under Start. The "Session history" link moved to the header.
4. **Running session.** The clock sits inside a ring that closes as the planned time passes (a stopwatch's ring goes round once an hour). "Websites blocked" and "Paused" are text on the line under the title, not pills. Today's target is one line with a small ring.
5. **Completion.** The XP is the large figure, then "Session complete", the focused time, the level bar, today's line and the next step.
6. **Other pages.** Streaks leads with the streak, then XP and gems, then freezes; states are coloured text, not badges; bars are thin with their figures on a line above. Blocking, History and Settings keep their rows, without outlines or badges.
7. **Around the web app.** The extension's popup, options and blocked pages and the desktop app's start page use the same palette. The app icon and favicon were not redrawn.

**Checked:** 653 web app unit tests, 243 extension tests, all 12 end-to-end tests, types; the design detector reports nothing; screenshots in light and dark at 1280px and at 390px, no sideways scroll. The backend was not changed by this work. Not tried: the desktop app's window (rebuild it with `npm run build` in `desktop/`), Safari and Firefox.

**Reviewed once** by a separate design reviewer (verdict: fix). Applied from it: the Streaks page's blue is kept for today's target only (the XP bar is green, the gem and freeze marks grey); a running or paused session shows today's minutes without the streak label, and no longer repeats the category under a title that already is the category; menus have a stroked chevron; links read alike. The fixes were not sent back for a second verdict.

**Open follow-ups from that review (optional, the user decides):**

1. Streaks: draw today's target as a large ring beside the streak count, and turn level, gems and freezes into plain rows, so the streak is the one large thing.
2. Completion: keep the closed ring on the "Session complete" screen instead of swapping it for a bar.
3. Motion: let the segmented control's thumb slide between choices.
4. Small: a larger tap area for the "Edit" and "Delete" links on a phone; "Username" in Settings set like the fields under it.
5. The app icon and favicon still use the old dark green.

**Tests changed with it:** the session form's tests (one radio group of lengths, task-free through the menu), `AppShell` (no account controls), `SettingsView` (Appearance, Sign out), `AppearanceControl` in place of `ThemeToggle`, and three end-to-end steps (sign out through Settings, History from the header, the stopwatch as a length).

## Focus time rules: done, pushed (30 October 2026, pushed 12 November 2026)

- **Paused time counts for nothing.** Pausing credits the focus so far; the pause is only a record. This replaces the "A2" decision. Past days and rewards were left as they were.
- **The time that finishes today is always a duration pill,** not only under 25 minutes.
- **Stopwatch sessions** (`timerMode`): count up, no plan, Stop completes them, 1 XP per whole minute focused.
- **Closing the laptop pauses the session** (`MachineSleepWatch`, `MachineSleepJob`). Tested by freezing a directly run backend process.

**Still to check by hand:** a real lid close, with the backend in Docker and with the desktop app. It relies on the backend's clock jumping on waking, and the Docker VM's clock could lag. Close the lid for 2+ minutes during a session; on waking it should be Paused with the clock stopped at about the moment it closed. Repeat with the extension connected. If Docker fails this, have the desktop app detect the gap and report it to the backend.

## Dashboard redesign: done, merged into master (21 October 2026)

The dashboard (`frontend/src/views/DashboardView.vue` and the session components) was reworked in five rounds with the Impeccable design skill (`.claude/skills/impeccable/`). Product context is in `PRODUCT.md`, the design system in `DESIGN.md`.

**State:** all five rounds are done and fast-forwarded into **master** and pushed to GitHub; the `dashboard-redesign` branch is deleted. Critique scores: 24 → 30 → 27 → 28 → 28 → 27 out of 40 (each later run used a fresh, stricter reviewer). Every report is in `.impeccable/critique/`. The rounds are recorded below, newest last, followed by the optional follow-ups.

**To see it in the desktop app** (the one in /Applications, which bundles its own copy of FocusQuest): rebuild it with `npm run build` in `desktop/` and install the new app (see `desktop/README.md`). The web app on port 8080 picks it up once its copy is rebuilt.

**Round three, done (21 October 2026):**

1. **End early is no longer a trap.** Once the server reports no time left, Complete is the main green pill, Pause the secondary one, and End early is gone; the clock keeps counting. If End early is chosen after the plan is met (paused past it, or the countdown just hit zero), the dialog offers "Complete session", or "Resume and complete" when paused. Completing does *not* unblock websites while today's target is unmet (blocking is on all day until then), so the dialog does not promise it.
2. **Whether a session counts.** The note under Start checks the form's current type and category; when they don't count toward today it says "This session won't count toward today's target." The end-early dialog now says "Until then you can unblock them early, at an XP penalty." The task-free explanation is plain text.
3. **The streak moment.** While a running session carries today over the target: "Your streak starts when this session ends" (or "Day N … when this session ends"), not "No streak yet". On completion, "Day N of your streak" is a serif stat with the flame, and a thin level bar sits under the XP ("Level 2 reached" when one is crossed). The flame is now one component, `StreakFlame.vue`.
4. **Type.** Young Serif has lining figures, so every display-font element now sets `font-variant-numeric: lining-nums` ("+35 XP" reads as one figure). A new `AppButton size="large"` pill is used for Start, Complete, Pause, Resume and Plan another.
5. **A sturdier ending.** The completion summary survives a reload that day until "Plan another" (`utils/completionSummary.ts`). After an early end the start page offers "Continue: <task> · N min" for the time it had left (`saveUnfinishedSession` in `utils/sessionDefaults.ts`).
6. **Keyboard and screen readers.** "Skip to content" link in `AppShell.vue`; the task field takes focus on a computer (not on touch screens); the paused tab title shows the time ("Paused 14:53 · FocusQuest"); focus moves to the completion screen.
7. **Polish.** "Complete opens when the time is up" sits quietly in the timer's status row instead of a line of its own. After an early end, the override link joins the one line under Start that says websites stay blocked, instead of a second line repeating it. The compact bar's fill edge can split a figure ("3|0"); both halves keep their contrast, so it is left as designed.

Checked: 623 frontend unit tests, all 11 end-to-end tests (two needed updating: the completion is now found as the "Session complete" region, and starting a session first dismisses a lingering summary), types and lint; screenshots in light at 1280px and dark at 390px, no sideways scroll at 390px. No backend code changed.

**Critique after round three (21 October 2026): 28/40** (trend 24 → 30 → 27 → 28). Report: `.impeccable/critique/2026-10-21T17-24-59Z__…`. Detector: 0 source findings; `cream-palette` is the known false positive; a real 3.7:1 contrast failure on the "Level 1" chip on /streaks (outside the dashboard).

**Decided by the user for round four:** fix both P1s together, take on all five issues, and keep the "Good evening / Ready to focus?" start page as it is.

**Round four, done and committed (21 October 2026):**

1. **The day's target.** The completion screen says "Websites are unblocked for the rest of today.", or "N min more today starts a streak. Websites stay blocked until then." When today's target is under 25 minutes away, the duration control offers the time that finishes it as a fifth segment (at least 5 min, "N min finishes today's target") and selects it unless a duration was picked.
2. **The running screen.** Today under a running or paused session is a muted text line over a 4px speculum-blue bar (reed green once reached), so the timer leads and the fill never cuts the figures.
3. **Continue.** "Continue where you left off" with the task and minutes on a wrapping line below; no boxed pill.
4. **Announcements.** The mid-session streak line is an always-present live region; info tips on the dashboard have specific labels ("About pausing", "About off-task time", …).
5. **Polish.** Override blocking moved to its own quiet line near the bottom, away from Start, with an info tip. The end-early dialog's cancel reads "Keep the session" when paused. The "Level 1" badge on /streaks uses text colour (the blue on its tint was 3.7:1). The "green sliver at 0:00" is one second of real progress, left as it is.

Checked: 626 frontend unit tests, all 11 end-to-end tests, types and lint; screenshots light 1280 and dark 390.

**Critique after round four (21 October 2026): 28/40** (trend 24 → 30 → 27 → 28 → 28). Report: `.impeccable/critique/2026-10-21T17-39-05Z__…`. Detector clean apart from the known `cream-palette`; the /streaks contrast failure is gone.

**Decided by the user:** one more round, then stop and merge `dashboard-redesign` into master. Override blocking stays on the dashboard. A completion short of the target leads with "Finish today · N min".

**Round five (the last), done (21 October 2026):**

1. **harden:** a duration chosen from the "finishes today's target" option is no longer saved as the next default; the one before it is kept. The rule for that duration is now one function, `finishTodayMinutes` in `utils/streak.ts`.
2. **clarify:** paused past the plan, Complete (resume, then complete) is the main pill, Resume secondary, no End early.
3. **delight:** a completion that counted and leaves today under 25 min short leads with "Finish today · N min" (same task, category, camera choice); "Plan another" is a link.
4. **colorize:** today's target is speculum blue on every screen (reed green once reached); the completion screen's level bar takes the XP's green.
5. **quieter:** the running status line is "Ends 7:58 PM" instead of "Complete opens when the time is up".

Checked: 629 frontend unit tests, all 11 end-to-end tests, types and lint; screenshots light 1280 and dark 390.

**Final critique (21 October 2026): 27/40** (trend 24 → 30 → 27 → 28 → 28 → 27). Report: `.impeccable/critique/2026-10-21T18-17-46Z__…`. Detector clean apart from the known `cream-palette`. Its two P1s were fixed before the merge:
- After a short completion the line named the remainder ("4 min more") beside "Finish today · 5 min"; it now names the offered session (`finishLine` in `utils/streak.ts`).
- The task-free note in the form was dropped; the line under Start already says whether the session counts.

**Optional follow-ups (the user's priority first):**
1. **Completion summary across devices:** the summary and "Continue" are kept in the browser's `localStorage`, so finishing in the desktop window shows a bare start page in Chrome. Needs the server to remember whether the latest completion summary was dismissed (a small backend change). `/impeccable harden`.
2. Start page after an early end: Continue as the main pill, the form folded behind "Plan something else". `/impeccable layout`.
3. Today's bar at low values: the label outside the bar while the fill is small (at 0 min it reads like an empty tag). `/impeccable bolder`.
4. Keys for Pause and Complete; the daily +10 XP bonus shown as its own line on completion.

**Seeing it without touching real data:** the review rounds used the isolated end-to-end stack, not the app on port 8080:

```bash
cd backend && ./gradlew bootTestRun --args=--spring.profiles.active=e2e     # :18080
cd frontend && VITE_API_BASE_URL=http://127.0.0.1:18080 npm run dev -- --port 15173 --strictPort
```

- `POST http://127.0.0.1:18080/api/e2e/reset` wipes it.
- `POST http://127.0.0.1:18080/api/e2e/clock/advance?seconds=N` moves its clock forward; reload the page after it.
- Stop both servers when done.

Left as they are on purpose:
- The clock's slashed zero: Atkinson Hyperlegible has no plain zero.
- The detector's `cream-palette` warning: the buff background is deliberate.

## Where things stand

Apart from the dashboard redesign above, everything planned is done; the other lists below are optional.

- **Desktop app (platform step 4): done on macOS.** `desktop/` is a Tauri app: a window on FocusQuest and a menu bar icon. It uses the Docker copy when that is running, otherwise starts its own, and quits blocked Mac apps while blocking is on. Tried live: Calculator was quit. Since 21 October 2026 it also closes blocked websites in Chrome (Apple events; macOS asks once to allow it) and opens at login. Build it with `npm run build` in `desktop/`; see `desktop/README.md`.

**For the semester (decided 21 October 2026):** the user runs the desktop app alone, from /Applications, and browses in Chrome; Docker stays off and is not needed. The app's own copy keeps its data in `~/Library/Application Support/FocusQuest`, separate from Docker's volume (Export and Restore in Settings move data between them).
- **Camera accuracy (C1d): done.** The `accuracy` command is built, away warns again, getting up no longer counts as looking away, and after calibrating again the looking-away margin stays at 5° (2 false seconds in 4½ minutes, no false warnings). Phone detection is weak and left as it is.
- **Every browser (platform step 3): done.** Chromium and Firefox; Safari, as its own small app "FocusQuest for Safari", connects and blocks (tried by the user 21 October 2026). Connecting needed two fixes: the bridge's match patterns, and a browser event Safari lacks (`docs/platform_roadmap.md`, "Safari").

The desktop app's window is not Safari and runs no browser extension, so since 21 October 2026 its Settings leaves the browser extension out (at the user's request) and the web app does not try to connect one there.

## Safari after a change

After the extension's code changes, in a terminal:

```bash
cd ~/Desktop/Code/focusquest/extension
npm run build:safari
open "safari-build/FocusQuest for Safari.app"
```

It should say **Signing with team …** (team P3NCC9658P, the Apple ID in Xcode). If it says **No Apple ID team found**: Xcode, **Settings**, **Accounts**, select the Apple ID, **Manage Certificates…**, **+**, **Apple Development**. Then check in Safari's **Settings**, **Extensions** that **FocusQuest** is still ticked and allowed on all websites, and reload http://localhost:8080.

## Optional, for you

- **The extension's options page** (5 minutes): only needed if FocusQuest ever runs somewhere other than port 8080. Steps in `docs/platform_roadmap.md`, "Waiting on the user", item 2.
- **Camera labelling in different light**, if you want the accuracy numbers to cover daylight and lamps (`docs/camera_roadmap.md`, "Start here").

### Decided, nothing to do

- Firefox every day: not needed (15 October 2026, the user does not use Firefox regularly).
- Phone detection: left as it is (15 October 2026).

**Remember:** quit the desktop app before starting Docker. If the app opened while Docker was down, it runs its own copy on port 8080 and keeps it; if `http://localhost:8080` then stops answering after Docker starts, run `orb restart docker`. After FocusQuest is updated, press Cmd+R in the desktop app's window to load the new version.

## Optional, for Claude (only after the user says go)

1. **Edge and Brave** by hand: load `extension/dist/` unpacked.
2. **Wording**: the backend comments and some docs still say "Chrome extension" where any browser is meant.
3. **Signing and notarising the desktop app**, so it opens on another Mac without a warning (needs a paid Apple Developer account).
4. From the requirements: a `BlockingOverride` record with a reason (section on overrides), and `GET /api/focus-sessions/{id}`.
5. A better way of seeing a phone in hand (camera finding 3).

**Looking inside Safari from the terminal** (how its connection was fixed): the user ticks **Allow JavaScript from Apple Events** (Safari **Settings**, **Developer**); then `osascript -e 'tell application "Safari" to do JavaScript "…" in (first tab of first window whose URL contains "localhost:8080")'`. For code that needs the extension's own rights, the user opens the extension's options page (Safari **Settings**, **Extensions**, **FocusQuest**, **Settings**) and the same command runs there.
