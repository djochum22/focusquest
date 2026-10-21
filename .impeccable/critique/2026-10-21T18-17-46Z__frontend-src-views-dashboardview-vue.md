---
target: dashboard
total_score: 27
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:/Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue"
target_fingerprint: "sha256:3ce635de53b91e0539e2f66cf9067895a88021cc19f2fb5a644728cb8153b3b0"
target_path: /Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue
timestamp: 2026-10-21T18-17-46Z
slug: frontend-src-views-dashboardview-vue
---
Method: dual-agent (A: design review · B: detector + browser)

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | "Ends 8:29 PM" and tab title good; empty today pill at 0 min |
| 2 | Match System / Real World | 3 | "streaks set to task-free sessions" jargon |
| 3 | User Control and Freedom | 3 | Cancel focused first, Continue, Change details |
| 4 | Consistency and Standards | 2 | "4 min more" vs "Finish today · 5 min"; outlined Pause as hero |
| 5 | Error Prevention | 3 | Past-plan end redirected to Complete |
| 6 | Recognition Rather Than Recall | 3 | Defaults remembered, Continue carries the task |
| 7 | Flexibility and Efficiency | 2 | No Pause/Complete keys; Enter doesn't trigger Finish today |
| 8 | Aesthetic and Minimalist Design | 3 | Start page after an early end crowded |
| 9 | Error Recovery | 3 | Fair dialog; "Ended early. 5 min counted toward today." |
| 10 | Help and Documentation | 2 | +15 XP = 5 + daily bonus, unexplained |
| **Total** | | **27/40** | **Acceptable** |

## Design Specificity Verdict
Copy is the most specific element (computed outcomes, finish-today, end→complete); structure remains category (greeting + form, big clock).
Deterministic: CLI 0. Browser (7 passes, idle / after early end / target reached; /streaks): only cream-palette on light (false positive). scrollWidth 390; muted text 5.63–9.44:1; small targets: "i" 18×18 (24px hit area), nav 24px, links 22px. Running/paused not overlay-scanned.

## Priority Issues
1. [P1] "4 min more today starts a streak" vs "Finish today · 5 min" (targetLine vs finishTodayMinutes min clamp) — utils/streak.ts, SessionComplete.vue. Fix: one source, or "The shortest session, 5 min, finishes today". /impeccable clarify
2. [P1] Task-free trap explained twice, in jargon — SessionForm.vue. Fix: drop the form note; keep the line under Start. /impeccable distill
3. [P2] Start page after an early end crowded — SessionPlanner.vue, DashboardView.vue. Fix: Continue as main pill; form behind "Plan something else". /impeccable layout
4. [P2] Completion summary / Continue only in the browser that saw them (localStorage) — completionSummary.ts, sessionStore.ts, backend. Fix: server-side dismissal. /impeccable harden
5. [P2] Today bar weak at low values (empty pill at 0; fill edge cuts "Toda|y") — TodayProgress.vue, ProgressBar.vue. Fix: label outside the bar while the fill is small. /impeccable bolder

## Persona Red Flags
Alex: no Pause/Complete keys; focus on header so Enter doesn't trigger Finish today.
Sam: autofocus skips greeting/today; blocking line not aria-describedby Start; pause consequence behind the "i".
Student: same greeting daily; nothing about yesterday/week; "No streak yet" everywhere.

## Minor Observations
Outlined Pause as the only large pill while running; dark paused low-energy; End early dialog paragraph 3 lines; Custom field off-grid.

## Questions to Consider
- Should the first question be "how much of today's 30 minutes now?"
- Should completion show the daily bonus as its own line?
- What should the dashboard remember of yesterday?
