---
name: FocusQuest
description: A calm, warm focus app where progress is earned quietly and distractions stay blocked.
colors:
  buff-page: "#f6f0e6"
  collar-white: "#fffcf7"
  softened-buff: "#ede2d0"
  eye-stripe-brown: "#3e2723"
  mottled-brown: "#6b5242"
  buff-hairline: "#d6c4a8"
  drake-green: "#006b3f"
  drake-green-deep: "#0f5132"
  speculum-blue: "#4b6d9f"
  chestnut: "#a3321e"
  chestnut-wash: "#fbece6"
  chestnut-deep: "#80260f"
  reed-green: "#2e6b2b"
  reed-wash: "#e9f1e1"
  bill-orange: "#8f5200"
  bill-wash: "#fdf1d8"
  dusk-black: "#111513"
  dusk-surface: "#1a201d"
  dusk-muted: "#27302b"
  dusk-collar: "#f1f2ee"
  dusk-gray: "#b6bbb5"
  dusk-hairline: "#3a4540"
  iridescent-green: "#4cc38a"
  iridescent-green-lit: "#6fd3a2"
  iridescent-ink: "#0b1410"
  speculum-blue-lit: "#8fa6d8"
  chestnut-lit: "#f5a48e"
  chestnut-solid-dusk: "#b8432c"
  reed-green-lit: "#a5d67a"
  bill-yellow: "#f6c04a"
typography:
  display:
    fontFamily: "Young Serif, Georgia, serif"
    fontSize: "2.5rem"
    fontWeight: 400
    lineHeight: 1.15
  headline:
    fontFamily: "Young Serif, Georgia, serif"
    fontSize: "1.5rem"
    fontWeight: 400
  stat:
    fontFamily: "Young Serif, Georgia, serif"
    fontSize: "1.75rem"
    fontWeight: 400
  timer:
    fontFamily: "Atkinson Hyperlegible Next Variable, system-ui, sans-serif"
    fontSize: "3.5rem"
    fontWeight: 700
    lineHeight: 1.1
    fontFeature: "tnum"
  title:
    fontFamily: "Atkinson Hyperlegible Next Variable, system-ui, sans-serif"
    fontSize: "1.05rem"
    fontWeight: 600
  body:
    fontFamily: "Atkinson Hyperlegible Next Variable, system-ui, -apple-system, Segoe UI, Roboto, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Atkinson Hyperlegible Next Variable, system-ui, sans-serif"
    fontSize: "0.9rem"
    fontWeight: 600
  small:
    fontFamily: "Atkinson Hyperlegible Next Variable, system-ui, sans-serif"
    fontSize: "0.85rem"
    fontWeight: 400
rounded:
  bar: "4px"
  field: "8px"
  dialog: "12px"
  pill: "999px"
spacing:
  xs: "0.25rem"
  sm: "0.5rem"
  md: "0.75rem"
  base: "1rem"
  lg: "1.5rem"
  xl: "2rem"
  page-max: "44rem"
components:
  button-primary:
    backgroundColor: "{colors.drake-green}"
    textColor: "{colors.collar-white}"
    rounded: "{rounded.field}"
    padding: "0.65rem 1.1rem"
  button-primary-hover:
    backgroundColor: "{colors.drake-green-deep}"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.eye-stripe-brown}"
    rounded: "{rounded.field}"
    padding: "0.65rem 1.1rem"
  button-secondary-hover:
    backgroundColor: "{colors.softened-buff}"
  button-danger:
    backgroundColor: "{colors.chestnut}"
    textColor: "{colors.collar-white}"
    rounded: "{rounded.field}"
    padding: "0.65rem 1.1rem"
  button-small:
    rounded: "{rounded.pill}"
    padding: "0.4rem 0.9rem"
  button-large:
    rounded: "{rounded.pill}"
    padding: "0.85rem 2rem"
  button-link:
    backgroundColor: "transparent"
    textColor: "{colors.mottled-brown}"
    typography: "{typography.small}"
    padding: "0"
  input:
    backgroundColor: "{colors.collar-white}"
    textColor: "{colors.eye-stripe-brown}"
    rounded: "{rounded.field}"
    padding: "0.6rem 0.75rem"
  progress-bar:
    backgroundColor: "{colors.softened-buff}"
    rounded: "{rounded.pill}"
    height: "1.75rem"
  status-pill:
    backgroundColor: "{colors.softened-buff}"
    textColor: "{colors.eye-stripe-brown}"
    rounded: "{rounded.pill}"
    padding: "0.15rem 0.6rem"
  dialog:
    backgroundColor: "{colors.collar-white}"
    textColor: "{colors.eye-stripe-brown}"
    rounded: "{rounded.dialog}"
    padding: "1.5rem"
---

# Design System: FocusQuest

## Overview

**Creative North Star: "The Quiet Mallard"**

FocusQuest borrows its colours from a mallard. By day it is the hen: buff paper, mottled browns, a dark eye-stripe for text. At dusk it is the drake: green-black water and grey body, lit by the iridescent head green. The duck stays calm on the surface and works hard underneath, and so does the interface. The page is quiet and warm, and the green appears only where the student acts or where progress is measured.

The mood is calm, warm and purposeful. Density is low: one narrow column (44rem at most) and generous vertical rhythm. Each page shows what the student needs right now, and detail sits one click away behind an info tip or a collapsed row. Progression (XP, gems, streaks) is visible but never loud. It shows up as big serif numbers and filled bars, not as celebrations.

This is not a generic SaaS dashboard (no stat-card grids, gradients or purple-blue tech palette). It is not a sterile productivity tool either (no cold grey-on-white blankness). Warmth comes from the buff ground and the serif, and calm comes from removing frames.

**Key Characteristics:**
- Warm buff ground in light mode, green-tinted near-black at dusk; both modes are first-class.
- No cards. Content sits on the page, divided by hairlines.
- Young Serif for titles and the figures the eye should land on; Atkinson Hyperlegible for everything else.
- One accent, drake green, reserved for primary actions and measured progress.
- Explanations hide behind an "i"; secondary actions are quiet underlined links.
- Pills for small controls and status; gently rounded rectangles for fields and main buttons.

## Colors

The palette is an earthy, low-saturation field (buff, brown, dusk black) with a single saturated green and a few plumage-derived state colours.

### Primary
- **Drake Green** (light) / **Iridescent Green** (dark): primary buttons, links, focus rings, timer fill and the default progress bar fill. In dark mode the bright green carries dark **Iridescent Ink** text so the button stays readable.
- **Drake Green Deep** / **Iridescent Green Lit**: hover state of primary buttons only.

### Secondary
- **Speculum Blue** (light) / **Speculum Blue Lit** (dark): the wing-patch blue, used only as the secondary progress-bar tone so two measures side by side (such as daily and weekly) can be told apart, and on the dashboard as the one tone of today's target on every screen (the session's own timer and the XP level bar keep the green). Never used for actions.

### Neutral
- **Buff Page** / **Dusk Black**: the page background (`--bg`).
- **Collar White** / **Dusk Surface**: raised surfaces such as the header bar, inputs, dialogs and tooltips (`--surface`).
- **Softened Buff** / **Dusk Muted**: empty progress tracks, neutral status pills and hover washes on quiet buttons (`--surface-muted`).
- **Eye-stripe Brown** / **Dusk Collar**: body text and headings (`--text`).
- **Mottled Brown** / **Dusk Gray**: secondary text, inactive nav, summaries, link-buttons and icons (`--text-muted`).
- **Buff Hairline** / **Dusk Hairline**: every 1px divider, field border and secondary-button outline (`--border`).

### State
- **Chestnut** (with **Chestnut Wash**, **Chestnut Deep**) / **Chestnut Lit** (with **Chestnut Solid Dusk**): errors, invalid fields and destructive buttons.
- **Reed Green** / **Reed Green Lit**, on **Reed Wash**: success and overtime. It is deliberately a different green from the accent.
- **Bill Orange** / **Bill Yellow**, on **Bill Wash**: notices, "blocking active" status and inactive-rule badges.

### Named Rules
**The One Green Rule.** Drake green means "act here" or "progress made". Never use it decoratively, for headings or for success messages; success has its own reed green.

**The Two Ducks Rule.** Every colour is a CSS custom property with a light value and a dark value (`frontend/src/style.css`). Never hard-code a hex in a component, and check every new screen in both modes.

## Typography

**Display Font:** Young Serif (with Georgia, serif)
**Body Font:** Atkinson Hyperlegible Next Variable (with system-ui, -apple-system, Segoe UI, Roboto, sans-serif)

**Character:** A sturdy, warm serif with a soft old-style voice (its figures, though, are set lining, so numbers stand as tall as the letters), paired with a sans designed for legibility. The serif gives the page its personality; the sans makes sure every label is easy to read. Both fonts are bundled locally (`@fontsource`), so the app works offline.

### Hierarchy
- **Display** (Young Serif 400, 2.5rem, 1.15): the dashboard planner title, one per screen; the "+XP" of a completed session (3.5rem, drake green).
- **Headline** (Young Serif 400, 1.5–1.75rem): page titles, the brand on sign-in screens, and the task title of a running or paused session.
- **Stat** (Young Serif 400, 1.75–2rem): XP, gems, streak count, owned freezes: the numbers the eye should land on.
- **Timer** (Atkinson 700, 3.5rem, 1.1, tabular figures): the session countdown only. Past the planned time it counts on with a plus ("+04:12") under a reed-green "Planned time reached"; paused, it steps back to muted brown. Atkinson's zero is slashed and the font has no plain alternate; that is part of its legibility.
- **Title** (Atkinson 600, 1.05rem): collapsible section titles and row headings.
- **Body** (Atkinson 400, 1rem, 1.5): all running text.
- **Label** (Atkinson 600, 0.9rem): form labels, nav and summaries (summaries at 400 in muted text).
- **Small** (Atkinson 400, 0.85rem): helper text, errors, tooltips, link-buttons and pills.

### Named Rules
**The Single Weight Serif Rule.** Young Serif has one weight. Every display element sets `font-synthesis: none`, so the browser never fakes a bold; hierarchy in the serif comes from size alone. Every display element also sets `font-variant-numeric: lining-nums`: the serif's default figures are old-style, so "+35" would sit smaller than "XP".

**The Honest Digits Rule.** The countdown stays in Atkinson with tabular figures, because the serif's digits are not of equal width and a ticking clock would jitter. Any figure that changes every second follows the same rule.

## Layout

A single centred column: the main area is `max-width: 44rem` with 1.5rem top, 1rem side and 3rem bottom padding. The header bar runs the full width with brand, nav, theme toggle, user name and sign-out, and wraps on narrow screens (`flex-wrap`, gap 0.75rem × 1.5rem). Sign-in and setup screens centre a 26rem column vertically on the page. Under 40rem the header becomes two rows: brand, theme toggle and an icon-only sign-out, then the four pages spread across the width; the user's name is dropped.

Vertical rhythm is built from rem steps (0.25, 0.5, 0.75, 1, 1.25, 1.5, 2). Stacks use flex columns with `gap`, usually 1rem inside a section and 1.25rem between session-state blocks. The dashboard's session states sit on an unframed "stage": the page itself is the surface.

Long lists and settings collapse to one line per item (title, muted summary, chevron) that opens in place, divided by hairlines.

## Elevation & Depth

Flat. Depth comes from tone (page vs. surface vs. muted surface) and 1px hairlines, not shadows. There are only two shadows, both small and functional: the floating info-tip, so it reads above the text it covers, and the theme toggle's thumb. Dialogs separate from the page with a 50% black backdrop rather than a shadow.

### Shadow Vocabulary
- **Tooltip lift** (`box-shadow: 0 4px 16px rgb(0 0 0 / 0.12)`): InfoTip popovers only.
- **Thumb** (`box-shadow: 0 1px 2px rgb(0 0 0 / 0.25)`): the sliding knob of the theme toggle.

### Named Rules
**The No-Card Rule.** Content is never boxed in a bordered, rounded panel. Separate groups with a hairline (`border-bottom: 1px solid var(--border)`) and space. The sign-in/setup column is the one surviving framed surface, and the legacy `.card` class is unused; do not revive it.

## Shapes

Two shape families. Interactive things that are small or inline (small buttons, status pills, badges, progress bars, sign-out, theme toggle) are full pills (999px). The main action of each dashboard state (Start focusing, Complete with Pause beside it, Resume, Plan another) is a large pill too. Larger fields and other main buttons are gently rounded rectangles (8px). Dialogs and the sign-in column use 12px. The thin timer bar uses 4px. The InfoTip button is a circle. Borders are always 1px.

## Components

### Buttons
Restrained and solid: one weight (600), no gradients and no movement on hover, only a colour change over 0.15s.
- **Shape:** gently rounded (8px); `size="small"` becomes a pill (999px) and is the size used inside panels; `size="large"` (pill, 0.85rem × 2rem, 1.05rem text, at least 10rem wide) carries a dashboard state's main action.
- **Primary:** drake green with collar-white text (iridescent ink in dark mode), padding 0.65rem × 1.1rem.
- **Secondary:** transparent with a hairline border; hover fills with softened buff.
- **Danger:** solid chestnut, for destructive confirmations only.
- **Link:** no padding, muted 0.85rem underlined text that turns full text colour on hover. Used for secondary or discouraged actions (End early, Override blocking, Change details, Unpair, Disconnect).
- **Unavailable actions are absent, not disabled.** Complete appears only once the planned time is reached; until then a muted line in the timer's status row says when that will be ("Ends 7:58 PM"). From then on Complete is the primary pill, Pause the secondary one, and End early is gone: ending would only forfeit the XP already earned. Paused past the plan, the same holds: Complete (which resumes and completes) leads, Resume is secondary.
- **Disabled:** 60% opacity, `not-allowed` cursor.

### Inputs / Fields
- **Style:** full-width, collar-white background, 1px hairline border, 8px radius, padding 0.6rem × 0.75rem, inheriting the body font.
- **Label:** above the control in Label type; an optional InfoTip sits beside the label, outside it.
- **Focus:** 2px drake-green outline with a 2px offset, shared by every input, select, button and link.
- **Error:** border turns chestnut (`aria-invalid`), and a Small chestnut message appears below.

### Pill Switches
- **Two-way switch** (session type): a hairline pill holding pill labels; the chosen one fills softened buff at weight 600.
- **Segmented control** (duration): the same, stretched to the width of the field beside it with equal segments (25, 50, 90, Custom). When today's target is closer than 25 minutes, the time that finishes it leads as a fifth segment (at least 5) and is chosen unless the student has picked a duration, with a muted "N min finishes today's target" below the control.
- Each radio is invisible and covers its own label, so a click, tap or test lands on the real input; radios in a group share a `name`, so arrow keys move between them.

### Status Pills / Badges
- **Status pill:** softened-buff pill, 0.85rem 600. "Websites blocked" stays neutral, since it is what the student asked for; only a session state ("Paused", "Interrupted") turns it bill wash with bill-orange text, sharing the same pill ("Paused · Websites blocked") rather than stacking a second one.
- **Rule badge:** outline pill, 0.75rem muted text; inactive rules use a bill-orange outline and text.

### Navigation
A text row in the header: muted links without underline; the current page is full text colour at weight 600. No pills, tabs or icons. Sign-out is a quiet ghost pill with an icon that washes softened buff on hover.

### Collapsible Section (signature)
The core organising pattern for Settings and History: a full-width row button with the title (1.05rem 600), a muted summary of the current value, and a 16px chevron that rotates 180° when open. Hovering underlines the title. A hairline sits below each section; the open panel is a 1rem-gap column.

### InfoTip (signature)
The way explanations are kept off the page: a 1.15rem circular "i" (Young Serif, muted outline, with an invisible 24px hit area) that opens a small surface popover above it on mouse hover, focus or tap, with the tooltip-lift shadow and a max width of 18rem. A second tap, a tap elsewhere or Escape closes it. On a phone (under 40rem) the popover docks along the bottom of the screen, 1rem from the edges. Closed tips are `display: none`, so they never widen the page. Only text the student must act on, camera consent, the pairing code and the blocking consequence of starting a session stay visible on the page.

### Progress Bar (signature)
A 1.75rem pill track in softened buff, filled with accent, secondary or success colour. Its label and value are drawn twice: once in text colour and once in contrast colour, clipped to the filled width, so the figures stay readable as the fill passes under them. Width transitions over 0.3s.

### Today's Target (signature)
`TodayProgress`: a speculum-blue progress bar reading "Today 20 / 60 min", turning reed green with "Today's target reached", and the daily streak beside it, outside the bar so the fill edge never cuts through it (a small bill-orange flame and "4-day streak", or muted "No streak yet"). While a running session carries today over the target, the note below turns reed-green 600, "Your streak starts when this session ends" (or "Day N of your streak when this session ends"), and "No streak yet" steps aside. Under a running or paused session it is compact so the timer stays the main measure: a muted 0.85rem line ("Today 10 / 30 min", streak at the right) over a 4px speculum-blue bar (reed green once reached), thinner than the session's own 8px bar, with the figures never on the fill. Its streak note is a live region, so it is announced when it appears. An optional note below it carries the milestone: muted "5 min more today starts a streak", or reed-green 600 "Day 1 of your streak" when a session has just crossed the target. It sits under the planner hero, below the controls of a running or paused session, and on the completion screen, and fills in live while a session that counts is running.

### Session Complete (signature)
Stays until the student chooses "Plan another", even across a reload that day: a muted "Session complete", the XP gained as a 3.5rem drake-green serif figure (lining figures) that rises in once (0.6s, ease-out, skipped under reduced motion), a thin 6px drake-green level bar (the XP's own colour) with "Level 2 · 85 XP to level 3" (or "Level 3 reached"), the focused time and task, and today's target. Under today's bar it says what the day means for websites: reed-green 600 "Websites are unblocked for the rest of today.", or muted "5 min more today starts a streak. Websites stay blocked until then." When today's target is under 25 minutes away and the session counted toward it, the main pill is "Finish today · 5 min" (same task, category and camera choice), the line above names that same session ("A 5 min session finishes today and starts your streak", never a bare remainder shorter than the shortest session), and "Plan another" steps down to a link. When the session just reached the target, the streak day is a stat of its own: the flame and "Day 5" in 1.75rem Young Serif, then muted "of your streak", and the small streak beside the bar is left out. Keyboard focus moves to the result when it appears.

### Dialog
A native `<dialog>`: collar-white surface, hairline border, 12px radius, 1.5rem padding, a 1.25rem Young Serif title, 50% black backdrop. At most min(28rem, 100vw − 2rem) wide.

## Do's and Don'ts

### Do:
- **Do** take every colour from the CSS custom properties in `frontend/src/style.css`, and check new screens in light and dark mode.
- **Do** separate content with 1px hairlines and space rather than frames.
- **Do** put explanations behind an InfoTip; keep visible only short text or what the student must act on.
- **Do** make secondary or discouraged actions link-buttons, and use `size="small"` pill buttons inside panels.
- **Do** collapse long lists and settings to one line per item that opens in place.
- **Do** say a consequence the student commits to (blocking that outlasts the session) in plain text where they commit, not in a tooltip.
- **Do** keep the page from scrolling sideways at 390px; check `scrollWidth` at phone width.
- **Do** state outcomes the app can work out ("That reaches today's target, so websites unblock now") instead of if/otherwise copy.
- **Do** keep the theme toggle neutral when on; green is not a decoration for switches.
- **Do** set big numbers in Young Serif with `font-synthesis: none`, and keep the timer in Atkinson with tabular figures.
- **Do** keep the page to a single column of at most 44rem.

### Don't:
- **Don't** wrap content in bordered, rounded cards, or revive the unused `.card` class.
- **Don't** use drake green for decoration, headings or success; success is reed green.
- **Don't** build a generic SaaS dashboard: no stat-card grids, gradients or purple-blue tech palette.
- **Don't** let it turn sterile: no cold grey-on-white, keep the buff ground and the serif.
- **Don't** add shadows beyond the tooltip and the toggle thumb; depth is tonal.
- **Don't** bold Young Serif or use it for the timer.
- **Don't** show detail a general student doesn't need (per-category rules, pairing or calibration dates).
