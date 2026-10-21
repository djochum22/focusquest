---
target: dashboard
total_score: 30
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:/Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue"
target_fingerprint: "sha256:b64469593ede0415569e3db66929735bbca2acd582e68684441dcb57f20fba34"
target_path: /Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue
timestamp: 2026-10-21T12-14-19Z
slug: frontend-src-views-dashboardview-vue
---
Method: dual-agent (A: design review · B: detector + browser)

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | No time in the tab title; reaching the planned time has no cue beyond Complete appearing |
| 2 | Match System / Real World | 3 | "daily streak is reached" vs "Today's target" in the End-early dialog |
| 3 | User Control and Freedom | 3 | Pause, End early, Cancel-first confirm; task not editable mid-session |
| 4 | Consistency and Standards | 3 | Target/streak wording drift; dialog title the only sans title |
| 5 | Error Prevention | 3 | Safe default focus; confirm copy is a conditional the app could resolve |
| 6 | Recognition Rather Than Recall | 3 | Remembers category and length; dialog makes the student work out the target state |
| 7 | Flexibility and Efficiency | 2 | No shortcuts, no repeat-last; 9-option category on first run |
| 8 | Aesthetic and Minimalist Design | 3 | Clean; two stacked questions; completion screen mostly empty |
| 9 | Error Recovery | 3 | Inline, plain errors; Try again |
| 10 | Help and Documentation | 3 | InfoTips work and dock on phone; "i" ~18px touch target |
| **Total** | | **30/40** | **Good** |

## Design Specificity Verdict
Surface authored (mallard palette, serif task title and +XP, no frames); skeleton still the standard pomodoro launcher. Before a session the "quest" layer is only today's bar; XP appears only after completion.
Deterministic: CLI 0 findings. Browser: light `cream-palette` on body (false positive, deliberate buff token); dark clean. scrollWidth 390 at 390px. All text pairs ≥4.5:1 incl. placeholder; --border on surface 1.67:1 (non-text, inputs).

## Priority Issues
1. [P1] Reaching the planned time is invisible and anticlimactic: clock freezes at 00:00, small overtime line, no tab-title cue (SessionTimer.vue, no document.title). Fix: remaining time in document.title, "Time's up" at zero; clock slot becomes "Planned time reached" / overtime counting up; Complete dominant, Pause demoted; live-region announcement. /impeccable harden, /impeccable delight
2. [P1] Streak milestones have no moment; ending early that reaches the target lands as a muted line above the greeting (DashboardView.vue ended line). Fix: name crossing the target ("Today's target reached · day 1 of your streak") in ended/complete states; on completion short of target say what's left. /impeccable delight, /impeccable clarify
3. [P2] End-early dialog speaks in conditionals the app can resolve (DashboardView.vue ConfirmDialog body). Fix: compute from streak.daily + uncreditedStreakSeconds: "You keep 8 min. That reaches today's target, so websites unblock now." Use "target" consistently. /impeccable clarify
4. [P2] Form weight inverted: optional task input is the hero, required 9-option Category is small; first-run validation stop. Fix: default category, or top-4 pills + More; aria-required. /impeccable distill, /impeccable layout
5. [P3] Progress bar end label straddles the fill edge ("No st|reak yet"); paused clock identical to running; slashed zeros in the clock. Fix: keep end label clear of the fill or move streak text out of the bar; dim paused clock. /impeccable polish, /impeccable typeset

## Persona Red Flags
Alex: no Space/Enter shortcuts, no repeat-last, XP/level not on dashboard, history buried.
Sam: 00:00 not announced, Complete appears without live region; Category lacks aria-required; target crossing not announced; InfoTip ~18px.
Mia: first-run category stop; no time in tab; End-early dialog makes her parse whether her course site unblocks; "Untitled task" headline.

## Minor Observations
Dark theme toggle track full green (decorative One Green); "Paused · Websites blocked" shares warning orange; Today bar heavier than the timer bar on the active screen; "Ended early" line above the greeting instead of near the bar; "Websites blocked" in notice orange reads as a warning for the desired state.

## Questions to Consider
- Why is XP the only celebrated number when the streak brings students back?
- Does Category need to exist for a student, or only serve streak rules that could default?
- At planned time, is the student done or in flow? The UI picks neither.
