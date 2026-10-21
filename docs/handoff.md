# Handoff: what to do next (21 October 2026)

Start here. The details live in two longer notes:
- `docs/platform_roadmap.md`: the container, every browser, the desktop app
- `docs/camera_roadmap.md`: camera verification

## Dashboard redesign: in progress (21 October 2026)

The dashboard (`frontend/src/views/DashboardView.vue` and the session components) is being reworked with the Impeccable design skill (`.claude/skills/impeccable/`). Product context is in `PRODUCT.md`, the design system in `DESIGN.md`.

**State:** two rounds of fixes are done, tested (frontend unit tests, backend tests, all 11 end-to-end tests), documented and committed on the branch **`dashboard-redesign`** (not merged into master yet). Continue on that branch. Critique scores so far: 24 → 30 → 27 out of 40. The last critique used a stricter reviewer, and two of its findings came from the second round: End early is now a trap past the plan, and the live bar contradicts the streak line. Each critique report is in `.impeccable/critique/`; the latest one (`2026-10-21T12-44-00Z__…`) is the to-do list below.

**Decided by the user for the next round:** fix both P1s together. Complete becomes the main green button once the planned time is reached. Take on all five issues.

**Next round, in this order** (in Claude Code: "run these all at once", or one `/impeccable <command>` at a time):

1. **harden: End early is a trap.**
   - When the server reports no time remaining, hide End early. Complete becomes the main green button and Pause is secondary; the clock keeps counting past the plan.
   - If the dialog still opens around that moment, it points to Complete instead of quietly losing XP.
   - Files: `ActiveSessionView.vue`, `endEarlyOutcome` in `utils/streak.ts`.
2. **clarify: say whether a session counts.**
   - The blocking note under Start checks the form's current choices (Task-free, category) with `sessionCountsToward`. When they don't count, it says "This session won't count toward today's target" instead of promising blocking it can't lift.
   - Reword "You can then override that".
   - Move the task-free explanation out of its info tip.
   - Files: `blockingSentence` in `utils/streak.ts`, `SessionPlanner.vue`, `SessionForm.vue`.
3. **delight: the streak moment.**
   - While a running session pushes today's bar over the target, the line beside it reads "Target reached · your streak starts when this session ends"; today it says "No streak yet".
   - On completion, give "Day N of your streak" the weight of a stat (serif, with the flame).
   - Add a thin level-progress bar under the XP.
   - Files: `TodayProgress.vue`, `SessionComplete.vue`.
4. **typeset:**
   - "+35" renders smaller than "XP", because Young Serif's digits are old-style. Size the figure so the number reads as big as the letters.
   - Resume is a rectangle while Start and Plan another are pills; match them.
5. **harden: a sturdier ending.**
   - Today's completion summary survives a reload until "Plan another".
   - After an early end, the start page offers "Continue: <task>" as one tap. Only the category and duration are remembered now, in `utils/sessionDefaults.ts`.
6. **audit: keyboard and screen readers.**
   - Add a skip link in `AppShell.vue`.
   - Put the cursor in the task field on desktop.
   - The paused tab title shows the time ("Paused 14:53 · FocusQuest", in `SessionTimer.vue`).
   - Move focus to the completion screen when it appears.
7. **polish:**
   - Replace the permanent "You can complete the session once the planned time is reached" line with something quieter.
   - After an early end, three blocking statements stack; make them one.
   - Check the compact bar's text where the fill edge splits letters.

Then run `/impeccable critique` to score the dashboard again.

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
