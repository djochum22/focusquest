---
target: dashboard
total_score: 27
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:/Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue"
target_fingerprint: "sha256:7d5f4ea7e9b13cc9315a3e07a71a7b0fd4f0f3dd8c3bd065d47da17f6dd4df8f"
target_path: /Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue
timestamp: 2026-10-21T12-44-00Z
slug: frontend-src-views-dashboardview-vue
---
Method: dual-agent (A: design review · B: detector + browser)

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Live bar reads "Today's target reached" beside "No streak yet" mid-session |
| 2 | Match System / Real World | 3 | "You can then override that" reads as override after the target |
| 3 | User Control and Freedom | 2 | Completion screen lost on reload; task never remembered |
| 4 | Consistency and Standards | 3 | Pill CTAs vs rectangular Resume; Complete secondary past the plan |
| 5 | Error Prevention | 2 | End early stays past the plan and silently forfeits XP; task-free counting hidden |
| 6 | Recognition Rather Than Recall | 3 | Category/duration remembered, task not |
| 7 | Flexibility and Efficiency | 2 | No skip link, no autofocus, no shortcuts, no "same again" |
| 8 | Aesthetic and Minimalist Design | 3 | Permanent "You can complete..." line; stacked blocking statements after an early end |
| 9 | Error Recovery | 3 | Clear inline errors |
| 10 | Help and Documentation | 3 | Phone tip docks far from its "i", no close affordance |
| **Total** | | **27/40** | **Acceptable** |

## Design Specificity Verdict
Skin authored (palette, serif, computed consequence copy); structure still the category's pomodoro launcher; "quest" (level, gems, run) absent until completion.
Deterministic: CLI 0. Browser: light cream-palette (false positive, deliberate buff); one unlocated `straggler` in an active state at 390px (low confidence). scrollWidth 390. Info-tip hit area 26px. All text pairs pass; --border 1.5–1.85:1 non-text.

## Priority Issues
1. [P1] End early is a trap once Complete is available (ActiveSessionView.vue End early link; streak.ts endEarlyOutcome). Fix: hide End early past the plan or have the dialog offer Complete (+XP) as primary; make Complete primary once reached. /impeccable harden, /impeccable clarify
2. [P1] blockingSentence ignores whether the chosen mode/category counts toward today (SessionPlanner.vue blockingNote; SessionForm task-free InfoTip). Fix: run sessionCountsToward on live form values; say "This session won't count toward today's target". /impeccable clarify
3. [P2] Target crossing underplayed and contradictory live: "reached" bar beside "No streak yet"; completion streak line small; "+35" old-style figures smaller than "XP"; no level context. Fix: live "streak starts when this session ends"; stat-weight streak line; lining numerals for XP; thin level bar. /impeccable delight, /impeccable typeset
4. [P2] Completion screen does not survive reload; task not remembered after an early end. Fix: persist last summary for the day until dismissed; prefill or "Continue: <task>". /impeccable harden
5. [P3] Keyboard: no skip link, no task autofocus, paused tab title drops the time. /impeccable audit

## Persona Red Flags
Alex: no shortcuts or "same again"; 9-item select; Complete secondary in overtime.
Sam: no skip link; tips open on focus while tabbing; focus not moved on completion.
Mia: 20 min fits no preset; retypes task; task-free may not count yet copy promises blocking until target; End early in overtime loses XP.

## Minor Observations
Permanent "You can complete..." line; three blocking statements after an early end; two-colour bar text splits glyphs at small size; paused screen has two drake greens; no voice change for bonus sessions after the target.

## Questions to Consider
- Where is the quest on the start page?
- Should End early exist once the plan is met?
- What does a calm but unmistakable streak moment look like?
