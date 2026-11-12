---
name: FocusQuest
description: A quiet focus app in the system's own typeface, with one thing per screen and a ring that closes as the work gets done.
colors:
  page-white: "#ffffff"
  ink: "#1d1d1f"
  graphite: "#636368"
  mist: "#f1f1f3"
  mist-pressed: "#e5e5e9"
  hairline: "#e2e2e6"
  go-green: "#1f7f38"
  go-green-deep: "#186a2e"
  ring-green: "#28a745"
  target-blue: "#0071e3"
  flame-orange: "#ff9500"
  stop-red: "#d70015"
  stop-red-deep: "#b00012"
  red-wash: "#fff0ef"
  green-wash: "#edf8ef"
  amber: "#b25000"
  amber-wash: "#fff4e3"
  page-black: "#000000"
  slate: "#1d1d1f"
  slate-lifted: "#2c2c2e"
  thumb-grey: "#48484b"
  paper-grey: "#f5f5f7"
  ash: "#a1a1a6"
  lit-green: "#30d158"
  lit-green-hover: "#4ade74"
  green-ink: "#03210c"
  lit-blue: "#2997ff"
  lit-red: "#ff6961"
  red-night-wash: "#2a1211"
  green-night-wash: "#0d2313"
  lit-amber: "#ff9f0a"
  amber-night-wash: "#2a1c05"
typography:
  display:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "clamp(2.25rem, 7vw, 3rem)"
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: "-0.035em"
  headline:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "2.125rem"
    fontWeight: 700
    lineHeight: 1.1
    letterSpacing: "-0.03em"
  timer:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "23cqw"
    fontWeight: 300
    lineHeight: 1
    letterSpacing: "-0.03em"
    fontFeature: "tnum"
  figure:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "3.5rem"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.04em"
    fontFeature: "tnum"
  stat:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "2rem"
    fontWeight: 700
    lineHeight: 1.1
    letterSpacing: "-0.03em"
    fontFeature: "tnum"
  title:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "1.75rem"
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: "-0.028em"
  dialog-title:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "1.3125rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.022em"
  row-title:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 500
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.47
    letterSpacing: "-0.011em"
  button:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 600
  detail:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
  label:
    fontFamily: "-apple-system, BlinkMacSystemFont, system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 500
rounded:
  thumb: "8px"
  track: "10px"
  field: "12px"
  field-large: "14px"
  dialog: "20px"
  pill: "999px"
spacing:
  xs: "0.4rem"
  sm: "0.75rem"
  base: "1rem"
  md: "1.25rem"
  lg: "1.75rem"
  xl: "2rem"
  page-top: "3rem"
  page-bottom: "4rem"
  header-height: "3rem"
  page-max: "42rem"
  dashboard-max: "32rem"
  auth-max: "22rem"
components:
  button-primary:
    backgroundColor: "{colors.go-green}"
    textColor: "{colors.page-white}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: "0.6rem 1.15rem"
  button-primary-hover:
    backgroundColor: "{colors.go-green-deep}"
  button-secondary:
    backgroundColor: "{colors.mist}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: "0.6rem 1.15rem"
  button-secondary-hover:
    backgroundColor: "{colors.mist-pressed}"
  button-danger:
    backgroundColor: "{colors.stop-red}"
    textColor: "{colors.page-white}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: "0.6rem 1.15rem"
  button-danger-hover:
    backgroundColor: "{colors.stop-red-deep}"
  button-small:
    rounded: "{rounded.pill}"
    padding: "0.35rem 0.85rem"
  button-large:
    rounded: "{rounded.pill}"
    padding: "0.85rem 2rem"
    width: "11rem"
  button-link:
    backgroundColor: "transparent"
    textColor: "{colors.graphite}"
    typography: "{typography.detail}"
    padding: "0"
  button-link-hover:
    textColor: "{colors.ink}"
  input:
    backgroundColor: "{colors.mist}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.field}"
    padding: "0.7rem 0.9rem"
  input-hover:
    backgroundColor: "{colors.mist-pressed}"
  task-field:
    backgroundColor: "{colors.mist}"
    textColor: "{colors.ink}"
    rounded: "{rounded.field-large}"
    padding: "0.95rem 1.1rem"
  segmented:
    backgroundColor: "{colors.mist}"
    textColor: "{colors.ink}"
    typography: "{typography.detail}"
    rounded: "{rounded.track}"
    padding: "2px"
  segmented-thumb:
    backgroundColor: "{colors.page-white}"
    rounded: "{rounded.thumb}"
    padding: "0.4rem 1rem"
  progress-bar:
    backgroundColor: "{colors.mist}"
    rounded: "{rounded.pill}"
    height: "6px"
  session-ring:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.timer}"
    size: "18rem"
  target-ring:
    backgroundColor: "transparent"
    size: "18px"
  info-tip:
    backgroundColor: "{colors.page-white}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.field}"
    padding: "0.7rem 0.85rem"
  dialog:
    backgroundColor: "{colors.page-white}"
    textColor: "{colors.ink}"
    rounded: "{rounded.dialog}"
    padding: "1.75rem"
    width: "27rem"
  header:
    textColor: "{colors.graphite}"
    typography: "{typography.detail}"
    height: "3rem"
    padding: "0 1.5rem"
---

# Design System: FocusQuest

## Overview

**Creative North Star: "The Closing Ring"**

FocusQuest shows one thing per screen, at the size it deserves. The start page is a greeting, one field, one control and one green button. A running session is a ring with the time inside it. The same ring, small, marks how far today's target has come. Everything else on a page is quieter than that one thing, or is one click away.

The page is white, or black in the dark, with near-black or near-white text. Type is the system's own typeface: heavy and tightly tracked in titles, light and large in the clock. Controls are filled grey shapes without outlines, so the only lines on a page are the hairlines between rows. Colour is spent on meaning: green where the student acts and where progress was made, blue for today's target, orange for the streak's flame.

Density is low. The signed-in pages sit in one centred column (42rem; 32rem on the dashboard) under a thin translucent bar that carries only the brand and the five pages. Explanations stay behind an "i", secondary actions are quiet text, and long lists collapse to one row per item. Progression (XP, gems, streaks) appears as large bold figures and thin bars, never as celebration.

**Key Characteristics:**
- A white page in light mode and a black page in dark mode; both are first-class.
- The system typeface only; no font is bundled.
- Filled controls without outlines; hairlines only between rows.
- No cards. Content sits open on the page.
- Every button is a pill; every either-or choice is a segmented control with a lifted thumb.
- A ring for time and for today's target; thin 6px bars for everything else that is measured.
- Green acts and records progress, blue is today's target, orange is the flame.

## Colors

A neutral page with three working colours, each tied to one meaning, plus red and amber for problems and notices. Every colour is a CSS custom property in `frontend/src/style.css` with a light value and a dark value.

### Primary
- **Go Green** (light) / **Lit Green** (dark), `--accent`: the fill of primary buttons, checked boxes and radios, and text that says progress was made (the "+35 XP" of a finished session). In the dark the bright green button carries **Green Ink** text (`--accent-contrast`) so the label stays readable; in the light it carries white.
- **Go Green Deep** / **Lit Green Hover**, `--accent-hover`: the hover fill of primary buttons only.
- **Ring Green** (light) / **Lit Green** (dark), `--accent-vivid`: the same green, brighter in light mode, for drawn progress: the session ring's arc, bar fills, the level bar, and the target ring once today's target is reached. It is for shapes, not for text.

### Secondary
- **Target Blue** / **Lit Blue**, `--accent-secondary`: today's target (the small ring and the daily streak bar), links in running text, focus rings (at 55% strength), the text selection (at 30%), the caret, and the "protected" line on the Streaks page. The gem and freeze marks are muted grey, so blue keeps its one meaning.

### Tertiary
- **Flame Orange** / **Lit Amber**, `--flame`: the streak's flame, and nothing else. A streak that has gone out draws the flame in the hovered-fill grey instead.

### Neutral
- **Page White** / **Page Black**, `--bg`: the page.
- **Page White** / **Slate**, `--surface`: raised things: dialogs, tooltips, and the chosen segment's thumb in light mode (**Thumb Grey** in the dark).
- **Mist** / **Slate**, `--surface-muted`: the fill of fields, quiet buttons, the segmented track, and empty ring and bar tracks.
- **Mist Pressed** / **Slate Lifted**, `--surface-strong`: the same fills when hovered, the InfoTip button, the divider inside the task field, and field fills inside a dialog (one tone further, because the dialog itself is raised).
- **Ink** / **Paper Grey**, `--text`: body text, titles and figures.
- **Graphite** / **Ash**, `--text-muted`: secondary text, inactive pages in the header, field labels, placeholders, summaries, link buttons, chevrons, and the clock and arc of a paused session.
- **Hairline** / **Slate Lifted**, `--border`: the 1px lines between rows and between page sections.

### State
- **Stop Red** / **Lit Red**, `--danger`, on **Red Wash** / **Red Night Wash**: error text, the 1.5px inset ring on an invalid field, missed periods, and the off-task warning. Destructive buttons are solid Stop Red with white text in both modes (`--danger-solid`), **Stop Red Deep** on hover.
- **Go Green** / **Lit Green**, `--success`, on **Green Wash** / **Green Night Wash**: "Planned time reached", "Websites are unblocked for the rest of today", a completed period, a saved setting. Success is the same green as the accent.
- **Amber** / **Lit Amber**, `--notice`, on **Amber Wash** / **Amber Night Wash**: "Paused", locked settings, inactive rules, an expired sign-in, a camera that is not checking.

### Named Rules
**The Three Meanings Rule.** Green means "act here" or "progress was made". Blue means today's target, or something to follow or focus. Orange means the streak's flame. Before colouring anything, name which of the three it is; if it is none, leave it in text or muted text.

**The Two Greens Rule.** Use `--accent` for green text and green buttons, and `--accent-vivid` for green shapes (arcs and bar fills). In light mode the vivid green is too pale for text, and the text green is too dull for a ring.

**The Two Modes Rule.** Take every colour from a custom property and check each new screen in light and in dark. The one colour set outside the tokens is the dark segmented thumb (Thumb Grey), because a white thumb would glare on black.

## Typography

**Display Font:** the system typeface (`-apple-system, BlinkMacSystemFont, system-ui`, then Segoe UI, Roboto, Helvetica Neue, sans-serif). On a Mac this is San Francisco.
**Body Font:** the same.

**Character:** One family doing two jobs: heavy and tightly tracked for titles, light and large for the clock. No font is bundled, so the app looks like part of the computer it runs on and starts without loading type. `--font-display` and `--font-body` are the same stack.

### Hierarchy
- **Display** (700, clamp(2.25rem, 7vw, 3rem), line-height 1.05, tracking -0.035em): the dashboard greeting and the title of the blocked page. One per screen, centred, balanced wrap.
- **Headline** (700, 2.125rem, 1.1, -0.03em): page titles (Streaks, History, Blocking, Settings) and the brand on sign-in and setup.
- **Timer** (300, 23% of the ring's width, about 4.1rem in an 18rem ring; line-height 1, -0.03em, tabular figures): the session clock. It drops to 17% once the text is longer than six characters (an hour or more). The blocked page uses the same weight at 4rem.
- **Figure** (700, 3.5rem, line-height 1, -0.04em, tabular figures): the streak count. The XP earned by a finished session uses the same style in Go Green at clamp(3.5rem, 14vw, 5rem).
- **Stat** (700, 2rem, 1.1, -0.03em, tabular figures): XP and gems on the Streaks page, with the unit beside it at 1rem 500 in muted text.
- **Title** (700, 1.75rem, 1.15, -0.028em): the task of a running, paused or planned session, and "Day 5" of a streak on the completion screen.
- **Dialog title** (700, 1.3125rem, 1.2): the heading of a dialog; "Session complete" uses the same size at 600.
- **Row title** (500, 1.0625rem; 600 when the row is open): collapsible rows in Settings; section names on the Streaks page are 600.
- **Body** (400, 1rem, 1.47, -0.011em): running text, field text, row text.
- **Button** (600, 0.9375rem; 0.8125rem small, 1.0625rem large): button labels.
- **Detail** (400, 0.875rem to 0.9375rem, usually muted): the header's pages, segmented labels (500, 600 when chosen), summaries, the line under a session title, link buttons, bar captions.
- **Label** (500, 0.8125rem, muted): field labels; the same size at 400 for errors, tooltips and the blocking note under Start.

### Named Rules
**The Light Clock Rule.** Only the session clock is weight 300. Everything else large is 700. Do not set other figures light, and do not set the clock bold.

**The Steady Digits Rule.** Any figure that changes while the student watches (the clock, minutes today, counts, XP) sets `font-variant-numeric: tabular-nums`, so it does not shift as it ticks.

**The Tight Titles Rule.** Tracking tightens as type grows: -0.011em for body, -0.022em for headings, -0.03em for page titles and the clock, -0.035em for the greeting, -0.04em for the largest figures. Never add positive tracking or uppercase labels.

## Layout

One centred column. The main area is 42rem wide at most, with 3rem above (2rem on a phone), 1.25rem at the sides and 4rem below. The dashboard narrows to 32rem and sits a little lower (up to 3rem of extra top padding), with its content centred: greeting, today's line, the task field, the lengths, Start, and one muted line about blocking. Sign-in and setup centre a 22rem column in the full height of the page. The blocked page centres a 24rem column.

The header is a bar 3rem tall that stays at the top while the page scrolls under it. It is a three-column grid: the brand at the left, the five pages (Focus, Streaks, History, Blocking, Settings) centred on the page. It carries no account controls: Appearance and Sign out live in Settings.

Stacks are flex columns with `gap`: 1rem inside a section, 1.25rem to 2rem between the parts of a session screen, 2rem between page sections, each set off by a hairline above and 2rem of padding. Settings, History and the rule lists are plain lists of rows between hairlines; a row opens in place.

Phone rules:
- **Under 40rem:** the brand leaves the header and the five pages spread across its width; the main area's top padding drops to 2rem; an open InfoTip docks along the bottom of the screen, 1rem from each edge.
- **Under 32rem:** a History row puts the title on its own line with the date and category beneath it.
- **Under 30rem:** the task and its category stack inside their one field, divided by a hairline, and the lengths drop "min" (kept for screen readers) so six segments fit.
- The page must not scroll sideways at 390px.

## Elevation & Depth

Flat. The page, the fields and the rows share one plane, and tone (page, fill, hovered fill) does the separating. Four things are allowed to sit above the page:

1. **The header.** The page colour at 78% over a 20px blur with 180% saturation, so content passing beneath stays faintly visible, closed by a 1px line of the hairline colour at 70%.
2. **Dialogs.** A raised sheet over the page dimmed with 38% black.
3. **Tooltips.** The InfoTip's popover, with the same raised shadow as a dialog.
4. **The segmented thumb.** The chosen segment lifts off its track.

### Shadow Vocabulary
- **Raised** (`--shadow-raised`; light `0 0 0 0.5px rgb(0 0 0 / 0.06), 0 8px 28px rgb(0 0 0 / 0.14)`; dark `0 0 0 0.5px rgb(255 255 255 / 0.12), 0 12px 40px rgb(0 0 0 / 0.6)`): dialogs, tooltips, and the skip link when it has keyboard focus.
- **Thumb** (`--shadow-thumb`; light `0 1px 3px rgb(0 0 0 / 0.14), 0 0 0 0.5px rgb(0 0 0 / 0.05)`; dark `0 1px 3px rgb(0 0 0 / 0.4)`): the chosen segment.

### Motion
One authored moment: when the session timer appears, its ring starts empty and sweeps to its place (1s, `--ease-out`, `cubic-bezier(0.16, 1, 0.3, 1)`); after that it moves a sliver each tick. Two smaller entrances exist: the XP figure of a finished session rises in once (0.7s), and a dialog scales up from 96% (0.28s). Everything else is a state change: fills and text colour over 0.15s, a button pressing in to 97%, a chevron turning over 0.25s, a bar or small ring filling over 0.6s, a tooltip fading up over 0.18s. Under `prefers-reduced-motion: reduce` every transition and animation is cut to nothing.

### Named Rules
**The No-Card Rule.** Never box content in a bordered or shadowed panel. Set groups apart with space and a hairline (`1px solid var(--border)`). The sign-in column is unframed too.

**The Four Lifts Rule.** Only the header, a dialog, a tooltip and the segmented thumb rise above the page. A new surface that wants a shadow should be one of those four or stay flat.

## Shapes

Round, filled, unoutlined. Every button is a full pill (999px), as are bar tracks and the header's page links. Fields are soft rectangles (12px); the dashboard's task field and the tinted message boxes are slightly rounder (14px). The segmented track is 10px with an 8px thumb inside a 2px inset, so the corners nest. Dialogs are 20px. The InfoTip button is a circle. Rings are drawn with round line caps: the session ring's stroke is 4.5 units of a 120-unit box (about 11px at 18rem), the target ring's is 3 of 20.

Controls have no border. The only 1px lines are hairlines between rows and sections, and the divider between the task and its category inside the task field. Focus is the one outline: 3px of Target Blue at 55%, offset 2px, on every input, select, button and link. An invalid field gets a 1.5px inset ring in Stop Red instead of a border.

## Components

### Buttons
Filled pills that change tone on hover and press in slightly when clicked.
- **Shape:** full pill (999px), no border, weight 600, 0.9375rem, padding 0.6rem × 1.15rem. Small: 0.35rem × 0.85rem at 0.8125rem, used inside rows and dialogs. Large: 0.85rem × 2rem at 1.0625rem and at least 11rem wide, used for the one main action of a dashboard state (Start focusing, Pause, Complete, Resume, Plan another).
- **Primary:** Go Green with white text (Lit Green with Green Ink in the dark); hover is one step deeper.
- **Secondary:** Mist fill with Ink text; hover is Mist Pressed.
- **Danger:** solid Stop Red with white text, for confirming a destructive action only.
- **Link:** no fill and no padding, muted 0.875rem text at weight 400; on hover it turns to full text colour and gains an underline (3px offset). Used for secondary or discouraged actions (End early, Change details, Sign out, Plan another when Finish today leads).
- **Press:** filled buttons scale to 97% while pressed. **Disabled:** 45% opacity and a `not-allowed` cursor.
- **In a confirm dialog:** Cancel (secondary) comes first and takes focus, so a stray Enter never confirms.

### Inputs / Fields
- **Style:** full width, Mist fill, no border, 12px radius, padding 0.7rem × 0.9rem, body type. Hover deepens the fill to Mist Pressed.
- **Selects:** drawn without the native look, with a small stroked chevron at the right (the same shape as the collapsible rows', in a mid grey that reads on both fills), so they are the same height in every browser.
- **Label:** above the control, muted 0.8125rem at 500; an optional InfoTip sits beside the label, outside it.
- **Focus:** the shared blue focus ring. The caret is Target Blue.
- **Error:** a 1.5px inset Stop Red ring (`aria-invalid`), with a 0.8125rem Stop Red message below. A form-level error is a Red Wash box with 12px corners and Stop Red text.
- **Disabled:** muted text and a `not-allowed` cursor.
- **Checkboxes and radios:** native, tinted with Go Green.

### Task Field (signature)
The dashboard's one field: a single Mist shape (14px corners) holding two controls. The task is typed at the left (1.0625rem, placeholder "What are you working on?"); the category is a muted select at the right, as wide as its text, behind a 1px divider. The focus ring is drawn around the whole shape, not around either control. "Task-free" is the last entry of the category menu; choosing it disables the task input. Under 30rem the two stack.

### Segmented Control
The control for every either-or choice (session length, Automatic/Light/Dark, Daily/Weekly, which rule list).
- **Track:** Mist fill, 10px radius, 2px inset, no border.
- **Segments:** 0.875rem at 500, padding 0.4rem × 1rem. The chosen one sits on a thumb (white in light, Thumb Grey in dark; 8px radius; the thumb shadow) at 600. Fill and shadow move over 0.2s.
- **Lengths:** on the dashboard the control stretches to the field's width with equal segments: the presets, then Custom, then Stopwatch last. When a short session would finish today's target, that length leads as an extra segment marked with a 12px target ring.
- Each radio is a real input, invisible and covering its own label, so a click, a tap or a test lands on it and arrow keys move within the group. A focused segment shows the blue ring.

### Navigation
A sticky 3rem bar, translucent and blurred. The brand is 0.95rem at 700. Each page is a 0.875rem muted link with pill-shaped padding (0.3rem × 0.75rem) and no underline; hover turns it to text colour; the current page is text colour at 600. No fill, no tab line, no icons. A skip link appears over the bar's corner on keyboard focus.

### Session Ring (signature)
The session timer: a ring up to 18rem across with the clock inside it. A Mist track, and a Ring Green arc with round ends that starts at the top and closes clockwise as the planned time passes. Inside, the clock (the Timer type) counts down, with one muted 0.875rem line under it ("Ends 11:27 PM"). Past the plan the clock counts up with a plus and the line turns Go Green at 600: "Planned time reached". Paused, the clock and the arc step back to muted grey. A stopwatch has no plan, so its ring goes round once an hour and the clock counts up. The ring sweeps to its place once when it appears. Above it sit the task (Title type) and one muted line ("Writing · 50 min planned · Websites blocked"); with no task typed the title is the category ("Writing session") and the line leaves the category out. A state such as "Paused" on that line is Amber at 600.

### Target Ring and Today's Line (signature)
Today's target is the same ring, small: 18px, a 3-unit stroke on a Mist Pressed track, Target Blue while filling, Ring Green once closed. It leads one centred muted line, "Today 10 / 30 min" or "Today's target reached", with the streak beside it: a Flame Orange flame and "4-day streak" in text colour at 600, or muted "No streak yet". A note may follow in muted 0.875rem; news the student earned ("Day 1 of your streak") is Go Green at 600. The line appears under the greeting, under a running or paused session, and on the completion screen. Under a running or paused session it carries the minutes only; the streak is left out so the clock stays the one thing on the screen.

### Progress Bar
For measures that are not the session or today's line: a line of text above (label at the left, muted figures at the right, 0.875rem, tabular figures) and a 6px pill track below, Mist with a Ring Green or Target Blue fill. Figures never sit on the bar. The fill moves over 0.6s.

### InfoTip (signature)
How explanations stay off the page: a 1.05rem filled circle (Mist Pressed, with a muted "i" at 600 and an invisible 24px hit area) that turns dark when hovered or open. It opens a small raised popover above it (white or Slate surface, 12px corners, the raised shadow, 0.8125rem text, at most 18rem wide) on hover, focus or tap; a second tap, a tap elsewhere or Escape closes it. Under 40rem the popover docks along the bottom of the screen. A closed tip is out of the layout, so it never widens the page. Only what the student must act on or agrees to stays in plain view.

### Collapsible Row (signature)
The organising pattern of Settings and History: a full-width row with a title (1.0625rem at 500, 600 when open), a muted summary of the current value at the right, and a 16px chevron that turns over when open. A hairline sits under each row and one above the list. The open panel is a 1rem-gap column with 1.75rem below it. History rows use the same pattern with fixed columns for date and category.

### Status Lines and Messages
State is said in coloured text, not in a badge: a 0.9rem line at 500 in Go Green ("Saved") or Amber ("Locked while website blocking is active"), with an InfoTip beside it when it needs explaining. Messages that interrupt (a form error, an expired sign-in, the off-task warning) are a tinted wash with 12px or 14px corners and no border.

### Dialog
A native `<dialog>`: white or Slate surface, no border, 20px corners, 1.75rem padding, the raised shadow, over 38% black. At most min(27rem, 100vw − 2rem) wide. The title is 1.3125rem at 700; body text is muted 0.9375rem; actions sit at the bottom right, small gap, Cancel first. Fields inside step one tone deeper so they still read as filled.

### Secondary Surfaces
The extension's popup, options and blocked pages and the desktop start page use the same palette, the system typeface, pills, 6px bars and the 300-weight clock, with their own small stylesheets.

## Do's and Don'ts

### Do:
- **Do** give each screen one main thing and size it accordingly: the greeting and Start on the start page, the ring while working, the XP figure afterwards.
- **Do** take every colour from the custom properties in `frontend/src/style.css`, and check new screens in light and dark.
- **Do** use the system typeface stack; set titles at 700 with tight tracking and the session clock at 300 with tabular figures.
- **Do** make controls filled shapes without outlines: Mist at rest, Mist Pressed on hover, the blue ring on focus.
- **Do** separate rows and sections with a 1px hairline and space.
- **Do** make every button a pill, and use the large size only for a dashboard state's main action.
- **Do** use a segmented control for an either-or choice.
- **Do** draw today's target in blue and the session in green, and keep orange for the flame.
- **Do** put explanations behind an InfoTip, make secondary or discouraged actions link buttons, and collapse long lists to one row per item.
- **Do** say a consequence the student commits to (blocking that outlasts the session) in plain text where they commit.
- **Do** put a bar's figures on a line above it.
- **Do** keep the main column to 42rem (32rem on the dashboard) and check that the page does not scroll sideways at 390px.
- **Do** keep account controls (Appearance, Sign out) in Settings.

### Don't:
- **Don't** wrap content in a bordered or shadowed card.
- **Don't** outline a control. Borders are for hairlines between rows.
- **Don't** bundle or load a font.
- **Don't** set text in Ring Green in light mode, or draw a ring or bar in the text green.
- **Don't** use blue for a primary action, or green for today's target before it is reached.
- **Don't** add a shadow to anything but a dialog, a tooltip or the segmented thumb.
- **Don't** put text on a bar's fill.
- **Don't** add motion beyond a state change; the ring's sweep is the one authored moment on a session screen.
- **Don't** put status in a pill or badge; say it in a coloured line of text.
- **Don't** add account controls, icons or a second row to the header.
- **Don't** show detail a student does not need now (per-category rules, pairing or calibration dates); put it one click away.
