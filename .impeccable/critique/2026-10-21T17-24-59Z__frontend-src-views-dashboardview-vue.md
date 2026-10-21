---
target: dashboard
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:/Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue"
target_fingerprint: "sha256:b0189ecca4c56abffb8415ec985657d089beb89a4a0fa7897c40d3d933df9d4d"
target_path: /Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue
timestamp: 2026-10-21T17-24-59Z
slug: frontend-src-views-dashboardview-vue
---
Method: dual-agent (A: design review · B: detector + browser)

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Completion never says whether websites are now unblocked |
| 2 | Match System / Real World | 3 | "Task-free time counts only toward streaks set to task-free sessions" is system jargon |
| 3 | User Control and Freedom | 3 | Pause, safe dialog default, Continue after early end; no undo for End session |
| 4 | Consistency and Standards | 3 | Bordered Continue pill unlike other secondaries; two green bars on the running screen |
| 5 | Error Prevention | 3 | Past the plan the dialog becomes "Resume and complete" |
| 6 | Recognition Rather Than Recall | 3 | Continue label truncates the task |
| 7 | Flexibility and Efficiency | 2 | No keys for Pause/Complete; Custom doesn't focus its field; no one-tap "finish today" |
| 8 | Aesthetic and Minimalist Design | 3 | Task-free: "won't count" said twice |
| 9 | Error Recovery | 3 | End-early outcomes computed and specific |
| 10 | Help and Documentation | 2 | Nothing explains why XP differs between sessions |
| **Total** | | **28/40** | **Good** |

## Design Specificity Verdict
Mostly authored (mallard palette, serif task over tabular clock, computed-consequence copy). Start page remains the Pomodoro template; progression invisible until a session ends.
Deterministic: CLI 0 findings. Browser: cream-palette on light pages (false positive, deliberate buff); low-contrast 3.7:1 on the "Level 1" chip on /streaks (real, outside target). scrollWidth 390; muted text ≥6.36:1 light, ≥9.44:1 dark; smallest targets: info "i" 18×18 visual, End early / history links 22px tall.

## Priority Issues
1. [P1] Completion is silent about blocking (SessionComplete.vue, utils/streak.ts). Fix: "Websites are unblocked for the rest of today" / "Websites stay blocked · N min to go" under the target bar. /impeccable clarify
2. [P1] 25-min default vs 30-min target: first session always lands short; nothing closes the gap (SessionForm.vue, SessionPlanner.vue). Fix: a "N · finishes today" preset when the gap is small, preselected after Plan another. /impeccable shape
3. [P2] Running-screen hierarchy inverted: 8px session bar vs 20px same-green Today bar; fill edge cuts "30" at 390 (TodayProgress.vue, ActiveSessionView.vue). Fix: speculum blue, slimmer. /impeccable quieter
4. [P2] Continue pill truncates the task and competes with the form as a second start path (SessionPlanner.vue). Fix: "Continue where you left off" + wrapping muted task line, or a link under Start. /impeccable layout
5. [P3] Live streak line not announced; paused InfoTip has generic label (TodayProgress.vue, PausedSessionView.vue). /impeccable audit

Reviewer A also flagged "26 min focused" vs "Today 27 / 30 min"; parent check: different measures (session vs day incl. an earlier minute), paused-time crediting matches the requirements; labelling only, not a defect.

## Persona Red Flags
Alex: no Pause/Complete keys; Custom doesn't focus minutes; Plan another clears the task; 9-option category select.
Sam: mid-session streak line and blocking-pill changes not announced; paused tip "More information".
Student mid-semester: daily greeting + form + a default that misses the target; XP/level hidden until completion.

## Minor Observations
Green sliver at 0:00; "Keep focusing" as cancel while paused; Override blocking adjacent to Start; floating "Session history →"; /streaks "Level 1" chip contrast 3.7:1.

## Questions to Consider
- Should the default be "finish today's target" rather than 25?
- If unblocking is the reward, why does the peak moment talk only about XP?
- Should a returning student land on their streak and one Start button?
