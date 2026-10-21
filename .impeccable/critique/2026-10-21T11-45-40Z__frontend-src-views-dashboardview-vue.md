---
target: dashboard
total_score: 24
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:/Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue"
target_fingerprint: "sha256:52f4e1135bd1a4fbc9569b7df52b7468ec311f0fa8698a3db30d8b10ff35698b"
target_path: /Users/douglasjochum/Desktop/Code/focusquest/frontend/src/views/DashboardView.vue
timestamp: 2026-10-21T11-45-40Z
slug: frontend-src-views-dashboardview-vue
---
Method: dual-agent (A: design review · B: detector + browser)

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Timer and blocking pill clear; today's streak progress never shown; completion notice fades after 6 s |
| 2 | Match System / Real World | 3 | Plain language; "Create session" then "Start session" exposes backend PLANNED state |
| 3 | User Control and Freedom | 2 | "Change details" deletes the plan and the form resets to defaults |
| 4 | Consistency and Standards | 2 | Abandon is a full button, not the quiet link DESIGN.md prescribes; disabled Complete outranks Pause |
| 5 | Error Prevention | 3 | Cancel focused in abandon dialog; required category only caught on submit |
| 6 | Recognition Rather Than Recall | 2 | Typed duration, no presets/last task; blocking-persists rule lives in a tooltip |
| 7 | Flexibility and Efficiency | 1 | No shortcuts, presets or repeat-last; two steps to start |
| 8 | Aesthetic and Minimalist Design | 3 | Calm idle state; session states bland rather than intentional |
| 9 | Error Recovery | 3 | Clear inline messages, Try again on load failure |
| 10 | Help and Documentation | 2 | InfoTips carry high-stakes rules; on touch they cannot be closed by tapping again and cover content |
| **Total** | | **24/40** | **Acceptable** |

## Design Specificity Verdict

LLM: half authored. Idle planner (serif "Ready to focus?", buff ground, pill input) is unmistakably FocusQuest. Active/paused screens are a generic Pomodoro layout (Atkinson title, big number, thin bar, three buttons). The dashboard of a streak-and-XP app shows no streak, XP or gems (DashboardView imports none of XpDisplay/GemDisplay/StreakProgress).

Deterministic scan: CLI 0 findings across DashboardView and session/progression/streak/common. Browser overlay: light `cream-palette` on body (false positive: the buff ground is the deliberate hen-mallard token); dark `low-contrast` placeholder 3.6:1 (real: no ::placeholder rule, Chromium default #757575). Extra: 390px viewport scrollWidth 605px from the hidden mode-switch radios inheriting width:100%. All token text pairs pass 4.5:1; --border is 1.5–1.85:1 as a UI edge.

## Priority Issues

1. [P1] Progression is absent from the dashboard and completion is a 6-second pill (DashboardView.vue:30). Fix: one quiet line under the hero ("Today 35 / 60 min · 4-day streak") with ProgressBar; a persistent completion state with serif "+XP", streak fill and "Plan another". Command: /impeccable delight, then /impeccable layout.
2. [P1] Blocking that outlasts the session is disclosed only in an InfoTip at commitment (SessionPlanner.vue:60-66). Fix: one visible sentence under Start with the real remaining figure. Command: /impeccable clarify.
3. [P1] Mode switch: radios have no name (SessionForm.vue:64-65), two tab stops, no arrow keys; absolutely positioned hidden radios with width:100% and no positioned parent cause horizontal scroll at 390px. Fix: name="taskMode", position:relative on fieldset, width auto on hidden radios; ::placeholder token. Command: /impeccable harden, /impeccable adapt.
4. [P2] Starting takes category (9 options, required) + typed duration + Create + Start; Change details wipes input. Fix: default category to last used, duration presets 25/50/90, single "Start focusing". Command: /impeccable distill.
5. [P2] Active-state hierarchy: disabled Complete is the most saturated element; Abandon equals Pause; session states lose the serif. Fix: hide or mute Complete until reachable, Abandon as link "End early", task title in Young Serif, merge status pills. Command: /impeccable polish, /impeccable typeset.

## Persona Red Flags

Alex: no Space/Enter shortcuts, no presets or repeat-last, 4+ interactions to start the daily 50-min session.
Sam: broken radio group; horizontal scroll on mobile/zoom; InfoTip not closable by second tap; role=timer with aria-label may mask the time; 6 s notice violates 2.2.1; disabled Complete reason not linked via aria-describedby.
Mia (student between classes): blocked by required category, double confirm, learns of persistent blocking only via hover, abandoning gives no "18 min counted", no sight of how close today's streak is.

## Minor Observations

InfoTip "i" in Georgia italic (off-system); paused state greys to look disabled; 10vh top padding leaves lower 40% empty; task input has only a placeholder label; greeting uses browser clock not account time zone; history only as a footer link; mobile header wraps to 3 rows.

## Questions to Consider

- Could anyone tell the running session is FocusQuest and not any Pomodoro tab?
- Why is the streak-and-XP product's home screen the one place with no streak or XP?
- Does the PLANNED step earn its click?
