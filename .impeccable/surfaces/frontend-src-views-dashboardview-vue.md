---
version: 1
slug: "frontend-src-views-dashboardview-vue"
primary_target: "frontend/src/views/DashboardView.vue"
related_targets: ["frontend/src/style.css","frontend/src/components/common/AppShell.vue"]
---

# Web app (all signed-in screens)

Scope: the whole web app, redesigned at the user's request on 11 November 2026: "take inspiration from Apple, come up with something new and fresh and especially decluttered". Mode: Operate. The user pinned the direction and asked not to be consulted, so no direction round was run.

Audience and job: a student at a Mac who opens the app to start a stretch of work, glances at it while working, and checks their streak. Every screen shows what is needed now and nothing else.

Constraints: one UI for the browser, the desktop window and phone width; light and dark both first-class; no data or behaviour changes on the backend; labels the tests and screen readers rely on are kept.

## Direction contract

THESIS: One thing per screen, at the size it deserves. The start page is a greeting, one field and one control; a running session is a ring with the time inside it. Refused: the stack of outlined pills, bars with text on them, and a header carrying account controls on every page.

OWN-WORLD: The system typeface (San Francisco on a Mac) in tight, heavy titles and a light, large clock. A white page (black in the dark), near-black text, soft grey filled controls with no outlines, hairlines only between rows. One green for acting and for the session's progress, one blue for today's target, orange for the flame. Pills for every button; a segmented control with a lifted thumb for every either-or choice.

STORY: The student sees where today stands, says what they are working on, picks a length and starts. While working they see only the time. Afterwards they see what it earned and the one next step.

FIRST VIEWPORT: A 3rem translucent bar: brand left, five pages centred. Below, centred in a 32rem column: the greeting as a 3rem bold title; one line with today's ring, minutes and streak; one filled field holding the task and its category; one segmented control of lengths ending in Stopwatch; the green Start pill; one muted line saying what blocking it commits to.

FORM: Pinned by the brief (Apple-inspired, decluttered); no roll. Signature move: the ring. The session timer is an 18rem ring that closes as the planned time passes, and the same ring, small and blue, marks today's target wherever it appears. Motion: the ring sweeps to its place once when the timer appears. seed: none (unavailable)

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
