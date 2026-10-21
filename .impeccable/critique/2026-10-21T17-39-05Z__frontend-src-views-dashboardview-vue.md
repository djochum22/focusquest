---
target: dashboard
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:/Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue"
target_fingerprint: "sha256:953d9834ecdb60e533187a575c7a50934dff0d4ee4efcc062ba365906892d524"
target_path: /Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue
timestamp: 2026-10-21T17-39-05Z
slug: frontend-src-views-dashboardview-vue
---
Method: dual-agent (A: design review · B: detector + browser)

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Timer, tab title, blocking pill, live today bar |
| 2 | Match System / Real World | 3 | "End early" can lead to completing |
| 3 | User Control and Freedom | 3 | Cancel focused first, Change details, Continue |
| 4 | Consistency and Standards | 2 | Today bar green on some screens, blue while running; End early comes and goes; three greens in dark |
| 5 | Error Prevention | 3 | Past-plan end redirected to Complete |
| 6 | Recognition Rather Than Recall | 3 | Defaults remembered, Continue carries the task |
| 7 | Flexibility and Efficiency | 2 | No Pause/Complete keys |
| 8 | Aesthetic and Minimalist Design | 3 | After an early end three ways forward compete |
| 9 | Error Recovery | 3 | Clear dialog copy |
| 10 | Help and Documentation | 3 | Info tips where questions arise |
| **Total** | | **28/40** | **Good** |

## Design Specificity Verdict
Skin authored (palette, serif, consequence copy); layout generic (clock, pill, thin bar); running screen most generic.
Deterministic: CLI 0. Browser: cream-palette on light pages only (false positive). Level chip contrast fixed. scrollWidth 390; muted text 6.36:1 / 9.44:1. Running/paused states not caught by B.

## Priority Issues
1. [P1] Finish-today duration saved as the next default (Custom 5 min) — SessionForm.vue onSubmit saveSessionDefaults. Fix: don't save durations from the finish preset. /impeccable harden
2. [P1] Paused past the plan: no Complete; "End early" link opens "Resume and complete" — PausedSessionView.vue, DashboardView.vue. Fix: Complete (resume+complete) primary, Resume secondary, no End early. /impeccable clarify
3. [P2] Today tone inconsistent (green / blue while running); three greens in dark — TodayProgress.vue, SessionComplete.vue, SessionTimer.vue. Fix: one tone for today, success only for reached. /impeccable colorize
4. [P2] Completion short of target has no direct next step — SessionComplete.vue. Fix: "Finish today · N min" primary, Plan another quiet. /impeccable delight
5. [P3] Running status line is negative ("Complete opens when the time is up") — ActiveSessionView.vue, SessionTimer.vue. Fix: "Ends 21:40". /impeccable quieter

## Persona Red Flags
Alex: no Pause/Complete keys; "Studying session" over "Studying · 5 min planned"; Custom default leak.
Sam: hidden h1 only; "End early" leading to completion.
Student: summary only in this browser's storage; Override blocking ever-present after an early end.

## Minor Observations
Cramped Custom segment at 1280; task focus ring louder than Start; big today bar label cut at low fill; running screen sits high on desktop; long task titles wrap to 4 lines on phone.

## Questions to Consider
- Should overriding live only on the Blocking page?
- What makes the running screen unmistakably FocusQuest?
- Should "N min finishes today" be the start page's main action?
