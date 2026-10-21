# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
Students who want to stay on schoolwork, working on a Mac at a computer, using the app daily. It started as a personal tool for its author's semester, and the intended audience is students generally. Each install has one local account.

## Product Purpose
FocusQuest helps a student commit to a defined stretch of work and keep to it. They plan a focus session, name the task, and the app blocks distracting websites and Mac apps for the duration. Daily and weekly streaks measure accumulated qualifying work; completing a whole session earns XP, levels and gems. Success is a student who finishes the work they set out to do and comes back the next day.

## Positioning
Duolingo-style progression (streaks, XP, levels) combined with real enforcement: sites are blocked in the browser, blocked Mac apps are quit, and an optional camera companion checks the student stays on task. Everything is local-first: no cloud, no social features, no AI monitoring service.

## Operating Context
- Backend: Java 21 / Spring Boot with an H2 file database; web app: Vue 3 + TypeScript, served by the backend on port 8080.
- Browser extension (Chromium, Firefox, Safari) blocks sites during a session; the Tauri desktop app (macOS) quits blocked apps and closes blocked Chrome sites.
- The web app runs in a browser and inside the desktop app's window; the desktop window runs no browser extension.
- Optional Python camera companion (OpenCV, MediaPipe) runs on the user's computer.
- Design docs live in `docs/` (requirements, architecture, user stories, roadmaps) and must be kept current with every change.

## Capabilities and Constraints
- Focus sessions with pause, task-based or timed; blocking stays active while paused.
- Separate concepts: streaks reward accumulated time, session completion rewards XP.
- History, streak views, blocking rules, settings, export/restore of data.
- Privacy: camera processing is on-device only; nothing leaves the computer. Consent points stay visible.
- One UI must serve the web, the desktop window and the extension flow; no platform-specific redesign.
- Undecided: whether it will be distributed to other students beyond the author.

## Brand Commitments
Game-like but calm: progression rewards real effort while the interface stays quiet and purposeful, not childish or nagging. Name: FocusQuest. Existing UI conventions (recorded by the user): no bordered cards, explanations behind info tips, quiet link-style secondary actions, collapsible rows, Young Serif for titles and big numbers, Atkinson Hyperlegible for everything else.

## Evidence on Hand
Working app and full documentation in `docs/`. No testimonials, user numbers or external users are on record; do not invent any.

## Product Principles
1. Enforce for real: blocking must work, not merely nag.
2. Reward effort honestly: progression reflects genuine focused work.
3. Stay calm: show only what the student needs now; detail is one click away.
4. Keep it private and local: no data leaves the computer.
5. One product everywhere: the same experience in browser, desktop window and extension.

## Accessibility & Inclusion
Must be legible: Atkinson Hyperlegible body font; light and dark modes both supported and checked.
