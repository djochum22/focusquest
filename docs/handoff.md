# Handoff: what to do next (21 October 2026)

Start here. The details live in two longer notes:
- `docs/platform_roadmap.md`: the container, every browser, the desktop app
- `docs/camera_roadmap.md`: camera verification

## Dashboard redesign: in progress (21 October 2026)

The dashboard (`frontend/src/views/DashboardView.vue` and the session components) is being reworked with the Impeccable design skill (`.claude/skills/impeccable/`). Product context is in `PRODUCT.md`, the design system in `DESIGN.md`.

**State:** three rounds of fixes are done. All three are committed on the branch **`dashboard-redesign`** (not merged into master yet). Continue on that branch. Critique scores so far: 24 → 30 → 27 out of 40 (the last from a stricter reviewer). Each critique report is in `.impeccable/critique/`; round three worked through the latest one (`2026-10-21T12-44-00Z__…`).

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

Then run `/impeccable critique` again.

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
