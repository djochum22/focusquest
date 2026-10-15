# Camera verification: where the work stands

This note is for picking the work up again, for example in a new Claude session. It says what is built, what comes next, and where the details live. The rules themselves are in the requirements specification (section 21, camera verification; section 19, privacy). How they are built is in the technical architecture.

Phase A closed out the MVP. Phase B built everything the camera needs before any computer vision was written, so the vision work only has to replace a fake detector with a real one. Phase C is that real detector, and making it usable every day.

## Start here

**Where things stand (15 October 2026).** Camera verification works end to end with the laptop's real camera, without a terminal:

- All three signals work live: away, looking away, and a phone in hand. Warnings come early as a heads-up (phone 5 s, looking away 15 s, away 90 s), with the 60-second grace period.
- The companion runs in the background (C3a, launch agent; log `~/Library/Logs/FocusQuest/companion.log`), calibrates from Settings (C3b), and the session screen says when it cannot use the camera (C2).
- **C1d, accuracy, is under way.** Labelled by the user on 15 October 2026 (`~/Documents/FocusQuest/`, eight files). The `accuracy` command is built (`e078e4f`); away warns again and getting up no longer counts as looking away (`06fcdc0`); parts A and B were labelled again (`5c6c7c8`); phone detection is left as it is (`a3e69fe`, the user's decision). The numbers are under "Measured accuracy" and "Step 3".
- **The companion is not paired** (since 9 October 2026, likely when the database moved into the container), so camera-verified sessions are not watched, and the calibration in use dates from 5 October.

**Next, by the user:** pair again (Settings, **Camera verification**, then `.venv/bin/focusquest-companion pair <code>` from `vision/`), calibrate from Settings, and label part A again (and part E if possible) with `.venv/bin/focusquest-companion label --category CODING`. How to label:

1. Do not run a session with "Verify with camera" while labelling; the background companion may keep running.
2. Click the camera window once, so it gets the keys. Press `1` on task, `2` away, `3` phone, `4` looking away as soon as you change what you do, and `q` to stop and save. The 2 seconds after each change are left out of the measurement, so a late key does not matter.
3. Each part 2 to 3 minutes (see "What the user labels" for the parts); `WRITING` instead of `CODING` for paper on the desk. Each run writes `~/Documents/FocusQuest/labels-<date>-<time>.csv`: numbers only, never a picture.
4. Different light (daylight, lamps) helps, if possible.

**Next, by Claude:** decide the looking-away margin from the new labels (see the end of "Step 3"), one change at a time, measured with `accuracy`.

**Before starting:**

1. Read this note, then `vision/README.md` (how the companion program runs and is built), then requirements §21 (the camera rules) and §19 (privacy).
2. Run `scripts/test-all.sh` to see everything pass before changing anything.
3. Ask the user whether they want the step as planned below. They review each step before it is committed.
4. The user's companion runs in the background with the code in `vision/` (an editable install). After changing companion code, restart it with `launchctl kickstart -k gui/$(id -u)/com.focusquest.companion`, and tell the user. After a backend change with a migration, the user restarts the backend.

## Status

| Step | What | Commit |
| --- | --- | --- |
| A1 | Open decisions in requirements §22 settled: XP, gems, penalty, completion, categories and more | `96c670f` |
| A2 | Streak freezes: 10 gems each, at most 2, daily only, only for whole gaps, bridge without counting (requirements §13) | `96c670f` |
| A3 | Complete export, and restore from Settings; tests for retried requests and for restarts | `272d453` |
| A4 | Wider end-to-end coverage | **Open.** 8 Playwright tests: 4 for sessions and blocking, 4 for the camera. Not covered end to end: the login form, streak progress, interruption |
| B1 | Specification and privacy rules for camera verification | `97afff4` |
| B2 | Consent in Settings, versioned (`CameraSettingsService.CONSENT_VERSION` and `utils/cameraConsent.ts` must match) | `2c99a93` |
| B3 | Per-category camera profiles: work area, warning timings, grace, minimum confidence | `eb6e2e7` |
| B4 and B5 | Observations become off-task episodes; off-task time is subtracted from the session and the streak; disputes | `e42f1c4` |
| B6 | Companion pairing: `fqc_` token, heartbeat and observation endpoints, connected status | `ea88436` |
| B7 | Warnings in the web app within 5 seconds (polling), with a countdown and "Not accurate?" | `0036c5e` |
| B8 | Camera in the session screens: "Verify with camera" switch, net focused time, history episodes | `285eed3` |
| B9 | The companion program (`vision/`), with a scripted fake detector; `serverTime` in the companion's answers | `03e4877` |
| B10 | End-to-end tests that drive a session through the real companion program | `03e4877` |
| C1a | Real camera, presence only: webcam, MediaPipe face detection, `--preview`, built-in camera chosen by name; away warning moved to 90 s | `94ae4d7` |
| C1b | Looking away: Face Landmarker head pose against the work area, `calibrate`, a lost face after a turn stays looking away | `9a863c2`; tried live, works |
| C1c | Phone in hand: EfficientDet-Lite0's "cell phone" overlapping a hand from the Hand Landmarker, with a rescaled confidence | `f433038`; tried live, works |
| C1e | Sensitivity: warnings after 5 s of phone and 15 s of looking away (grace stays 60 s); looking away from 5° beyond the zone, at full confidence; phones and hands also looked for in enlarged crops of the lower frame | `e133627`; tried live, a lot better |
| C3a | The companion starts by itself: `install` writes a macOS launch agent; `--background` waits to be paired. Calibrated zones at least 20° by 14°; the log gives the head's angles when a look away starts | `df537c8`; tried live, works |
| C3b | Calibration started from Settings, guided in the camera window; Settings shows when it was calibrated or why it failed | `1c59161`; tried live, works |
| C2 | Say in the web app when the camera cannot be used: `cameraProblem` with each report, `CAMERA_UNAVAILABLE`, the reason in the session banner | `42337c8`; tried live, works |
| C2b | No usage logging to Google: the background companion runs in a sandbox that allows connections only to this computer | `e888f94`; tried live: camera, warnings and calibration work, MediaPipe's uploads fail |
| **C1d** | **Label mode (`0e37818`), accuracy measurements (`e078e4f`), tuning (`06fcdc0`, `5c6c7c8`, `a3e69fe`); the looking-away margin is open** | **Under way** |

Another session also changed website blocking in `3d6a796`: sites are now blocked every day until the daily target is reached, and throughout any running session.

## Decisions the user has made

- **Warnings come early, as a heads-up; the grace period stays at 60 seconds.** Away after 90 seconds (was 3 minutes), a phone in hand after 5 (was 20), looking away after 15 (was 60). The user wants to know at once, and still have a minute to put the phone away before time stops counting. Set in `focusquest.camera.warning-after` in `backend/src/main/resources/application.yml`, and in requirements §21.
- **The laptop's built-in camera** by default, not the iPhone (Continuity Camera). `--camera` picks it by name; see "The real detector" below.
- **Off task never pauses the session.** Time stops counting after the grace period and counts again when the user is back; the session keeps running. Automatic pausing was offered and not taken up.
- **Camera profiles are defaults only**; users cannot edit them yet (requirements §22, #17).
- **BlazeFace stays for presence; the Face Landmarker is added for head pose only.** The landmarker gives no per-face score, and the graded away confidence is wanted for C1d's accuracy measurements. (Since C1d step 3 the face's score is recorded on its own, and away's confidence runs from 1 down to 0.7 rather than 1 minus the score.)
- **A face lost just after a turn away is looking away**, not away, until a face is found again (within 2 seconds of the last turned-away look), for at most 5 seconds (C1d step 3, 15 October 2026; it was unlimited).
- **Phone confidence is rescaled in the companion**, not by lowering the backend's minimum: the detector scores held phones at only 0.3 to 0.8, so a phone overlapping a hand is reported from 0.7 (at a score of 0.25) to 1 (at 0.6). The overlap with a hand is what makes it believable.
- **EfficientDet-Lite0, float16** for phones, over Lite2: on sample photos it caught 3 of 5 held phones against Lite2's 2, at about 12 ms a frame against 31.
- **Sensitivity (C1e) before accuracy (C1d)**: a first adjustment from what the user saw live, then measure.
- **No terminal for everyday use (C3) before C1d.** The companion runs natively on the Mac, not in a container: Docker on macOS cannot reach the camera, open windows or show notifications. A launch agent starts it, and Settings starts calibration. Detection in the browser was considered and not taken: browsers throttle background tabs.
- **Pairing stays a one-time terminal step** (inside `install`). Pairing from the browser needs an app bundle's URL scheme or a local listener; it is left for later, with a menu bar app.

## How the pieces fit

- **Backend** (Spring Boot, `backend/`, package `vision`): consent, profiles, pairing, calibration requests, the companion endpoints, and all the rules. From observations it works out episodes, warnings and subtracted time (`OffTaskCalculator`, `OffTaskService`). The companion never decides what is off task; it only reports what it sees.
- **Web app** (Vue, `frontend/`): Settings (`CameraVerificationCard`: consent, pairing code, the companion's status, "Calibrate", what each category checks), the "Verify with camera" switch, the warning banner (`OffTaskBanner`), net focused time and history.
- **Companion program** (Python, `vision/`): paired with a `fqc_` code, it reports every 2 seconds and looks once a second. It opens the camera only while the backend's answer says `cameraOn`, and calibrates when it says `calibrate`. It sends observations: stretches of one signal (`AWAY`, `PHONE`, `LOOKING_AWAY`) with a confidence and a start and end, stamped in the backend's clock (`serverTime`). It turns the backend's warnings into system notifications. On macOS it runs in the background through a launch agent (`service.py`). `vision/README.md` has the details and a table of its files.
- **End-to-end** (`e2e/`, Playwright): `e2e/tests/camera.spec.ts` runs the companion program for real with a scripted detector.

## Done: no usage logging to Google

While trying C2 live, the companion's log showed `portable_clearcut_uploader.cc:90] Failed to send to clearcut: FAILED_PRECONDITION: Not valid for uploading until: <about 15 minutes after start>`.

- MediaPipe 0.10.35's native library (`mediapipe/tasks/c/libmediapipe.dylib`) contains Google's usage logging ("Clearcut"): `SolutionEvent` records (which task ran; events such as starting, running, errors) sent to `https://play.googleapis.com/log` through NSURLSession. Nothing suggests frames are sent. The failures were attempts before its upload window opened, so records may have been sent before the fix. No switch to turn it off was found.
- The user chose to block it. The launch agent now runs the companion under `/usr/bin/sandbox-exec` with `service.ONLY_THIS_COMPUTER`: everything allowed except outgoing connections, which may go only to `localhost` (127.0.0.1, ::1, and the name `localhost`). Checked outside the companion: under it, NSURLSession to `play.googleapis.com` fails with "Operation not permitted", and Python and NSURLSession still reach 127.0.0.1.
- Only when the pairing's backend is on this computer (`service.is_this_computer`); otherwise `install` says the connections are not limited. A terminal `run` is not sandboxed. `install` downloads the models first, outside the sandbox.
- `install` also tries `launchctl bootstrap` up to 5 times, a second apart: right after `bootout`, launchd sometimes still refuses with "Bootstrap failed: 5".
- §19 now says the companion connects to nothing but the backend.
- Tried live: the camera, a warning's notification and calibration from Settings all work in the sandbox, and MediaPipe's uploads now fail at once with `Failed to send to clearcut: INTERNAL: ResponseInfo[... code: 2 ...]`.

## Next: C1d, accuracy

**Goal.** Know how often each signal is right, and tune from that rather than from guesses. This is the research question in requirements §21: "How accurately can a local computer-vision system detect absence, looking away and smartphone use during a focus session, without storing video?"

**Where it starts.** Every threshold is still a guess from sample photos and from what the user saw live: the looking-away margin (5°), its confidence curve and the minimum zone size (20° by 14°) in `calibration.py`; the phone thresholds (`PHONE_FROM` 0.25, full confidence at 0.6) and the lower-frame crops in `phones.py`; the presence threshold (`PRESENT_FROM` 0.5) in `camera.py`; and the warning timings in `focusquest.camera.*` (`backend/src/main/resources/application.yml`). Nothing has been measured.

### Ask the user first

Settled on 8 October 2026: the plan as written, and all four answers as proposed (keys in the camera window; `~/Documents/FocusQuest`; the sandbox; the whole labelling plan). The questions were:

1. **Keys.** Labelling with keys pressed in the camera window (proposed: `1` on task, `2` away, `3` phone, `4` looking away, `q` to stop), or in the terminal.
2. **Where the CSV goes.** Proposed: a path the user gives, defaulting to `~/Documents/FocusQuest/labels-<date>-<time>.csv`. It stays on the computer and is never sent anywhere.
3. **The sandbox.** A terminal run is not sandboxed, so MediaPipe would try to send Google its usage records during labelling (see "Done: no usage logging to Google"). Proposed: `label` starts itself again under `/usr/bin/sandbox-exec` with `service.ONLY_THIS_COMPUTER`, as the launch agent does. The first time, macOS may ask for the camera again.
4. **How much labelling.** Proposed: the plan under "What the user labels", about 20 to 30 minutes in all, in one sitting or several.

### Step 1: the `label` command

**Built** (8 October 2026), as planned below. As built: `CameraDetector.last_readings` (a `Readings`, in `camera.py`) holds the raw readings; `labelling.py` holds the `Labeller` and the copy of the profiles, checked by a test against `CameraProfiles.java` and `application.yml`; `Preview.notes` shows the label as the window's headline and `Preview.next_key` takes the keys; `service.sandboxed` builds the sandboxed command for both the launch agent and `label`, which starts itself again under it with `FOCUSQUEST_SANDBOXED=1` after downloading the models. The CSV's columns: `time, category, label, away, phone, looking_away, face_score, yaw, pitch, phone_score, held_phone_score, hands` (empty where not looked at: angles only for a face found where looking away is checked, hands only once a phone scored 0.25). `held_phone_score` is the best phone overlapping a hand at any score, so `PHONE_FROM` can be raised, and `CERTAIN_FROM` moved, on the recorded file; lowering `PHONE_FROM` below 0.25 cannot be tried, since hands are not looked for below it. Checked with the real models on MediaPipe's sample portrait inside the sandbox; not yet run with the webcam.

`focusquest-companion label --category CODING [--out FILE] [--camera NAME]`: the real `CameraDetector`, with the category's profile, in a preview window, without a session or the backend.

- **What it writes.** Once a second, one CSV row of numbers, never a picture (§19):
  - the time and the current label;
  - what was reported, with confidence: away, phone, looking away;
  - the raw readings behind it: the best face score, the head's yaw and pitch, the best phone score, whether that phone overlapped a hand, and how many hands were found.

  The raw readings are what make tuning possible without labelling again: a new threshold can be tried on the same file.
- **Getting the raw readings.** `CameraDetector.detect` returns only a `Detection`. Proposed: it also keeps the latest readings in a small dataclass (for example `Readings`, as `detector.last_readings`), filled in `detect` and `_head`. `Detection` and the companion stay as they are.
- **Labels lag.** The user presses the key a moment after changing what they do. Proposed: the CSV keeps every second, and the measurement leaves out the 2 seconds after each label change.
- **The profile.** The `Profile` a session's category would get, built in the companion from a copy of the table in `CameraProfiles.java` (work area per category; every category checks away and phone). There is no backend to ask. A test should check the copy against requirements §21's table.
- **The background companion** may keep running. On macOS two programs can read the camera at once (found during C2), and the background one opens the camera only during a camera-verified session. Do not run such a session while labelling: its warnings would make no sense.
- **Tests** with the fakes in `tests/test_camera.py` (no OpenCV or MediaPipe): the rows written, the label changes, nothing but numbers in the file.

### What the user labels

Sitting as they usually work, after calibrating (Settings, "Calibrate"). Each part at least 2 to 3 minutes, with a few changes of label inside it.

| Part | Category | What to do | What it measures |
| --- | --- | --- | --- |
| On task | CODING | Normal work at the screen, including reading its corners and edges | False looking away and false phone |
| On task | WRITING | Read and write on paper on the desk, then back to the screen | False looking away where the desk counts |
| Away | CODING | Leave, half leave (at the edge of the picture), come back | Missed and false away |
| Phone | CODING | Phone held up, at desk height, low in the lap, half out of the picture, lying on the desk untouched | Phone in hand found; a phone on the desk not counted |
| Looking away | CODING | Turn to each side, look up, look far down, glance away briefly | Missed looking away, and the margin |

Lighting matters: if possible, once in daylight and once with lamps.

### Step 2: measure

`focusquest-companion accuracy FILE...` reads one or more CSV files and prints:

- **Per signal, per second:** how often it was reported when the label said so (recall), how often the label said so when it was reported (precision), and the false reports per minute on task.
- **Per episode:** whether it would have warned at the profile's timings (phone 5 s, looking away 15 s, away 90 s), and how many false warnings an hour of on-task time would give. It replays the backend's rules for this: minimum confidence 0.7, gaps of up to 5 seconds merged. `OffTaskCalculator.java` stays the source of truth; the Python copy only needs to be close enough to estimate.
- **Trying thresholds** (`--margin 7`, `--phone-from 0.3`, and so on), worked out again from the raw readings without labelling again.

A second source is real use: the data export (Settings) contains `cameraObservations` and the disputes, so the episodes the user marked "Not accurate?" are labelled false alarms. Few so far, but they come from real sessions.

**Built** (15 October 2026), as planned above, in `accuracy.py`; the README has how to run it. As built: `replay` applies `CameraDetector.detect`'s rules to the recorded readings (with `Calibration.looking_away`'s new `margin` and `phones.confidence`'s new `phone_from` and `certain_from` parameters), `observations` feeds the seconds through the companion's own `ObservationTracker`, and `episodes` is the Python copy of `OffTaskCalculator.episodes`, which also says which signal warned. With today's thresholds the replay gives exactly what was reported in all 452 recorded seconds; when it does not (after calibrating again) the report says so. A labelled stretch counts as warned about in time if a warning for its signal came between its start and 2 seconds after its end; a warning is false if the label was on task at it and for the 2 seconds before. Beyond the plan: it also gives the seconds sent in stretches less sure than the minimum, which turned out to be what stops away warnings. With no files given it reads every `labels-*.csv` in `~/Documents/FocusQuest`. Not done: reading the disputes from a data export.

### Measured accuracy

**15 October 2026, first labelling.** Lighting not recorded. Thresholds as of C1e (present from 0.5, margin 5°, phone from 0.25 and certain from 0.6, minimum confidence 0.7), against the calibration of 5 October: the companion's pairing had stopped working on 9 October (likely when the database moved into the container), so calibrating from Settings before labelling cannot have worked. Looking away may be judged against an out-of-date calibration. Six files, 452 seconds: 158 on task (133 CODING, 25 WRITING), 195 away, 63 phone, 36 looking away. Less than planned: parts A and B are 21 and 25 seconds rather than 2 to 3 minutes, so the false alarm rates are rough.

| Signal | Found when so | Right when reported | False while on task |
| --- | --- | --- | --- |
| Away | 85 of 184 s (46 %) | 85 of 86 s (99 %) | 0 of 142 s |
| Phone | 5 of 59 s (8 %) | 5 of 9 s (56 %) | 0 of 142 s |
| Looking away | 9 of 25 s (36 %) | 9 of 106 s (8 %) | 12 of 142 s (5.1 a minute) |

Warnings: none came, right or wrong. One away stretch, two phone stretches and one looking-away stretch were long enough to be warned about.

What it shows, in the order to take it on in step 3:

1. **Away never warns.** Away's confidence is 1 minus the best face's score, and a stretch is as sure as its least sure second (`ObservationTracker`). Something faint the face detector takes for a face (scores 0.3 to 0.5) turns up now and then while nobody is there, so each away stretch ends up at 0.50 to 0.56, below the backend's 0.7, and all 110 seconds sent were ignored. Options: report away at a fixed confidence (no face at 0.5 or above means away), or start a new stretch when the confidence drops, rather than lowering the whole stretch.
2. **Leaving, or half leaving, is mostly reported as looking away** (71 of the seconds labelled away): the head turns as the user gets up, the face is lost, and "a face lost just after a turn stays looking away" holds until a face is found again, however long that takes. Leaning to the edge of the picture also keeps a turned face in view. Limiting how long a lost face counts as looking away (say 10 seconds, then away) would fix the first; the second is arguably right, since it is off task either way.
3. **The phone is mostly not seen at all.** In 51 of the 63 seconds labelled phone the object detector scored no phone above 0.2, and in 4 where it scored 0.5 to 0.76 no hand was found. No threshold the files can try changes that; it needs a better look (larger crops, another model) or a different signal.
4. **Paper on the desk:** in part B the head turned 20 to 38° to either side, outside the desk zone (yaw -15 to 14°, pitch 1 to 18°), so 8 of 25 WRITING seconds were looking away. The calibrated desk zone is also shallow for a desk. Ask the user where the paper lay; calibrating the desk where it actually lies, or a wider desk zone, may be enough.

Tried on the same files (before the step 3 changes below): `--margin 10 --present-from 0.6` finds looking away in 16 of 25 seconds and halves the false seconds on task (6 of 142), and finds away in 98 of 184.

### Step 3: tune, and smooth only if needed

**Under way** (15 October 2026). One change at a time, each measured on the same six files:

| Change | Why | Away found | Away right | Away false on task |
| --- | --- | --- | --- | --- |
| Before | | 85 of 184 s (46 %) | 85 of 86 (99 %) | 0 of 142 s |
| 1. Away's confidence from 1 down to 0.7 just below a face score of 0.5 (`camera.away_confidence`), not 1 minus the score | While away, the face detector nearly always scored something at 0.1 to 0.5 (only 16 of 195 away seconds had no face at all); on task, faces fell below 0.5 in only 4 of 158 seconds. 0.5 separates the two; 1 minus the score only made every away stretch too unsure to count | 99 of 184 (54 %) | 99 of 106 (93 %) | 2 of 142 s |
| 2. A face lost after a turn stays looking away for at most 5 seconds, then is away (`camera.LOST_TURNED_FOR`; `accuracy --lost-for`) | Lost after a turn, the face was gone for 1 to 2 seconds in real looks away and 6 with the phone held up, but 55 when getting up. 3, 5 and 10 seconds gave 150, 148 and 144 away seconds found; 5 leaves room above the looks away seen | 148 of 184 (80 %) | 148 of 156 (95 %) | 2 of 142 s |

Looking away and phone are unchanged by both. The 2 false away seconds are single seconds of a face scored 0.3 to 0.43: a single look is never an episode. Away still did not warn about the one 101-second away stretch: a face was seen at full score for one second in it (likely the half-in-the-picture part, labelled away too), splitting it into 22 and 62 seconds with a 6-second gap, one more than the backend merges. Not tuned from one sample.

**Parts A and B labelled again** (15 October 2026, 112 seconds CODING and 79 WRITING, all on task), still against the calibration of 5 October (the companion was still unpaired). With all eight files: 333 seconds on task, no false warning of any kind. Looking away while writing on paper (finding 4) did not recur: 1 false second of 79. While coding, 14 of 112 seconds were looking away, the head 12 to 13.5° down against a screen zone that ends at 6.7° (plus the 5° margin): looking at the lower screen or the keyboard.

| Looking away, all eight files | Found | Right when reported | False while on task |
| --- | --- | --- | --- |
| Margin 5° (today) | 9 of 25 | 9 of 71 (13 %) | 27 of 333 s (4.9 a minute) |
| Margin 7° | 9 of 25 | 9 of 38 (24 %) | 9 of 333 s |
| Margin 10° (before C1e) | 9 of 25 | 9 of 25 (36 %) | 5 of 333 s |

Not changed yet: the calibration is ten days old and the false seconds come from the head pitched just below its screen zone, so calibrating again comes first, then measuring again. The user asked for 5° in C1e, so going back to a wider margin is theirs to decide.

Next: pair the companion again and calibrate, then label part A (and E) again and decide the margin; the phone (finding 3) is left as it is for now (the user's decision, 15 October 2026): phone in hand is weak, found in 8 % of labelled seconds. Offered and not taken yet: looking for hands every second and for a phone in an enlarged crop around each (recommended), or EfficientDet-Lite2. Either needs part D labelled again.

- Change the thresholds where the measurements say so, one at a time, and write down why.
- Add smoothing (a majority over a second or two, in the companion) only if single missed frames break episodes: the backend already merges gaps of up to 5 seconds.
- Training a model of our own (PyTorch) only if the pretrained ones fall short. That needs saved images, which needs its own consent decision under §19.

### Done when

- The user has labelled every part in the table above.
- The measured rates are written down in a "Measured accuracy" section of this note: per signal, the per-second rates and the false warnings per hour, with the date, the lighting and the thresholds used.
- Any tuning follows from those measurements, with the before and after numbers.
- `scripts/test-all.sh` passes.

### Things to know

- **Opening the camera turns on the user's camera.** Leave live runs to the user; check the code with fakes and with still images (see "Things to know about MediaPipe").
- **Restart the background companion** after changing its code (`launchctl kickstart -k gui/$(id -u)/com.focusquest.companion`), and tell the user.
- **On macOS, another app using the camera does not stop the companion**, and switching off its camera permission takes effect only when the camera is next opened.
- **Commit only after the user has reviewed**, one step per commit. The `label` command and the `accuracy` command can be two steps.

## The real detector (C1)

`CameraDetector` in `vision/src/focusquest_companion/camera.py`, behind the `Detector` interface (`open`, `detect(now, profile)`, `close`) in `detector.py`, chosen with `run --camera`. `ScriptedDetector` implements the same interface for tests and demos (`run --script "present 60s, phone 90s"`).

**Built so far:**
- The webcam (`Webcam`) is read on a background thread that keeps only the latest frame, in memory. It logs the frame size when it opens (the user's MacBook Air camera: 1920 by 1080).
- `MediaPipeFaces` runs MediaPipe's BlazeFace (short range). No face at 0.5 or above is `AWAY`, with a confidence from 1 (no face) down to 0.7 (a face just below 0.5); before C1d step 3 it was 1 minus the best face's score.
- Models are downloaded once to `~/.cache/focusquest/models` and checked against a pinned SHA-256 (`models.py`).
- A camera that cannot open, or stops sending frames, raises `DetectorUnavailable`. The companion says so once and retries at each report.
- `--preview` shows the frame, the face box and the result in a window (`Preview.annotate`: text that grows with the picture, on a dark band, the first line in yellow).
- **C1b, looking away:** where the profile checks `LOOKING_AWAY`, `MediaPipePoses` runs the Face Landmarker (`face_landmarker.task`, pinned) and `pose_from_matrix` turns its facial transformation matrix into yaw (positive towards the picture's right) and pitch (positive down). The matrix's camera space is x right, y up, z towards the camera; this was checked by shifting a sample image. `calibration.py` holds the rule: beyond `MARGIN_DEGREES` (5 since C1e; it was 10) outside every zone of the work area (screen; screen or desk) is looking away, with confidence `(0.7 + 0.3·min(1, beyond/15)) × min(1, face score/0.7)`. Calibrating (from Settings, or `focusquest-companion calibrate`) keeps 5 seconds at the screen, then 5 at the desk, trims 5 % at each end, makes each zone at least 20° by 14°, and saves only the angles in `calibration.json` beside the pairing file; without it, `calibration.DEFAULT` is used and `run` says so. When a look away starts, the head's angles and the work area go to the log.
- **C1c, phone in hand:** where the profile checks `PHONE`, `MediaPipePhones` runs EfficientDet-Lite0 (float16, pinned) for COCO's "cell phone" class only. Only once a phone scores `PHONE_FROM` (0.25) or more does `MediaPipeHands` run the Hand Landmarker, at detection and presence thresholds of 0.3 rather than 0.5: a hand gripping a phone shows little palm and was missed at 0.5. `phones.py` holds the rule: the surest phone whose box overlaps a hand's landmark box is `PHONE`. Since C1e, both models also look at two enlarged crops of the lower frame (see "Done: C1e"). `--preview` outlines phones (red when in a hand) and hands.
- `cameras.py` picks the camera. OpenCV numbers macOS cameras in the order of their unique IDs (it sorts them so, in `cap_avfoundation_mac.mm`). `system_profiler SPCameraDataType -json` gives each camera's name and unique ID, so the program finds the built-in camera by name and looks its number up at each opening. `focusquest-companion cameras` lists them.

**Things to know about MediaPipe:**
- Install with `.venv/bin/pip install -e '.[camera]'` in `vision/`. The unit tests need neither OpenCV nor MediaPipe: camera code is tested with fakes.
- Only the Tasks API exists (`mediapipe.tasks.python.vision`); the old `mp.solutions` is gone. Each task needs a model file, which does not ship with the package.
- **1.0.1 aborts on macOS** as soon as a detector starts (in its Metal helper, even with the CPU delegate), so the `camera` extra pins `mediapipe<1`. 0.10.35 works. Check any new model against 0.10.x.
- When Claude ran MediaPipe from its shell, it ran outside the sandbox.
- Check a detector without the webcam by feeding it images. MediaPipe's sample portrait, `https://storage.googleapis.com/mediapipe-assets/portrait.jpg`, gives one face at 0.92. Do not commit third-party images.
- Opening the webcam turns on the user's camera. Leave live tests to the user.

## Done: C3, the companion without the terminal

The user should not need a terminal for everyday use: not to start the companion, not to calibrate it. Built in two steps.

**C3a: the companion starts by itself** (`df537c8`; `service.py`, `cli.py`).
- `install` pairs if needed, downloads the models, writes `~/Library/LaunchAgents/com.focusquest.companion.plist` (`RunAtLoad`, `KeepAlive`, restart after 30 s, Aqua sessions only) running `<venv python> -m focusquest_companion --config <path> run --camera --background`, and bootstraps it with `launchctl` into `gui/<uid>`. `uninstall` boots it out and deletes it; `status` reads `launchctl print`.
- `--background` waits for a pairing (checked every 5 s), waits again after a refused token, and puts the time on each log line. A terminal `run` without it behaves as before (the end-to-end tests match its lines from the start), and warns when the agent also runs.
- Tried live: macOS asked for the camera permission for the background program, and warnings arrived as notifications.
- Found in that try: after recalibrating, the user was reported looking away for minutes while working. The new screen zone was 3° wide and 2.6° tall: calibration measures the head, and reading across a screen mostly moves the eyes. With the 5° margin nearly every pose was off task; the countdown ran on, correctly, from what was reported. Hence the minimum zone size (`MIN_YAW_SPAN` 20, `MIN_PITCH_SPAN` 14, `Zone.widened`, also applied when loading older calibrations) and the head's angles in the log.

**C3b: calibration from Settings** (`1c59161`).
- Backend: `POST /api/me/companion-calibration` (409 unless camera verification is on and the companion is paired and connected, and no camera-verified session is ACTIVE), `calibrate` in the companion's answer, `POST /api/companion/calibration` (`STARTED`, `CALIBRATED` with `calibratedAt`, `FAILED` with `message`), columns on `companion_credentials` (V15), and `CompanionCalibration.of` working out the state; a request not picked up, or a calibration not finished, within 2 minutes is `FAILED`. Only the time and outcome are stored, never the angles.
- Companion: `Companion` calls a `Calibrator` when asked, after saying `STARTED`, and once per connection says when its saved calibration was made (the file's time); a scripted companion says it has nothing to calibrate. `CameraCalibrator` (`camera.py`) opens its own `Webcam`, runs `guided_calibration` (a 3-second "Get ready" countdown, then 5 seconds kept, for the screen and then the desk, in a window asked to stay in front, `WND_PROP_TOPMOST`), saves, and hands the angles to the running `CameraDetector` (`use_calibration`). The terminal `calibrate` uses the same flow; its `--preview` is still accepted and does nothing.
- Web app: a "Calibrate" button and status line under the companion's status, followed every 2 seconds while under way. End-to-end: the scripted companion answers "Calibrate" with its failure.
- While calibrating (about 16 seconds) the companion does not report, well within the 30 seconds it counts as connected.
- Tried live: it works, and the window came to the front. At the user's request its text was made larger and easier to read.

**Later, not planned yet:** a menu bar app (an icon showing whether it watches, a preview on demand, the camera permission in FocusQuest's name), and pairing from the browser.

## Done: C1e, sensitivity

The user tried C1b and C1c live: both worked, but both were too forgiving. C1e is a first adjustment, made before C1d measures anything (`e133627`). Tried live after calibrating again: a lot better.

**Looking away counts sooner.**
- Before: a head counted only beyond `MARGIN_DEGREES` (10) outside the calibrated zone, and its confidence reached the backend's 0.7 only about 7 degrees further: about 17 degrees in all.
- Now: the margin is 5, and the confidence is `LEAST_CONFIDENCE` (0.7) just past the margin, growing to 1 over `CERTAIN_AFTER_DEGREES` (15). A face found below `CLEAR_FACE_FROM` (0.7) lowers it in proportion. All of this is in `calibration.py`.
- If reading the screen's edges now counts as looking away, the first fix is to calibrate again across the whole screen; after that, a margin of 7 or 8.
- Not changed: the default zones in `calibration.DEFAULT` (the user has calibrated).
- With the earlier warnings, a 5-second glance at a phone, or a 15-second look 5° beyond the screen, pops a notification. That is what the user asked for.
- Unknown: BlazeFace's score for a turned head live. If it drops below 0.7, the confidence drops with it. The preview shows the face's score.

**A phone counts when it is small or half out of the picture.**
- Why it was missed: EfficientDet-Lite0 looks at a 320×320 copy of the whole frame, so a small or cut-off phone is a few pixels and scores below `PHONE_FROM` (0.25). The hand was missed for the same reason.
- Now: `MediaPipePhones` and `MediaPipeHands` look at the whole frame and again at two enlarged crops of its lower part (`phones.closer_looks`: lower left and lower right, each 60 % of the width and height, overlapping in the middle). `phones.distinct` drops a phone found again in a crop. A phone and a hand still have to overlap.
- Measured on 5 held-phone and 4 desk stock photos (kept in a scratchpad, not committed), each also shrunk to a half and to a third ("far"), and for the found phones shifted until the phone is half below the bottom edge ("cut"). Of the 15 versions of the three held phones the detector can see at all, it now finds 9; before, 5. It finds none of the desk phones. The one false positive is a laptop under a typing hand at a third of its size, which the old rule also counted; a built-in laptop camera does not see its own laptop.
- Tried and left out, because they caught nothing more and added a false positive: counting a phone *near* a hand, and a lower threshold (0.15) for a phone touching the frame's edge.
- Cost: up to about 70 ms per look for phones and hands together, once a second.

## Where the decisions live

- **Camera rules** (off-task signals, profiles, warning then subtraction, when time is settled, disputes, failure behaviour): requirements specification §21.
- **Privacy:** §19.
- **Freezes and streak loss:** §13.
- **Open questions and their answers:** §22. #5, on how long an abandoned session's websites stay blocked, was settled by the daily-blocking change.
- **Off-task time model, companion endpoints (calibration included), token scoping and the web app pieces:** technical architecture. Search for "Off-task time model", "Calibrating the companion program from Settings", "Pairing the companion program", "Off-task warnings in the web app" and "Camera verification".
- **How the companion program works and runs, in a terminal and in the background:** `vision/README.md`.

## Known limits

- Where off-task time crosses midnight or the start of the week, the day split can be off by up to a second.
- Observations that arrive after their stretch of time was settled change nothing already settled.
- Users cannot edit the camera profiles yet; this was decided (requirements §22, #17).
- When the camera closes (pause, end), observations from its last 2 seconds that were not reported yet are dropped.
- A user who turns away and then gets up within 2 seconds is reported as looking away until they are back, not as away. Both warn; looking away warns sooner (15 s against 90 s).
- The launch agent points at `vision/.venv`'s Python. Moving or recreating the venv, or a Homebrew Python upgrade that breaks it, stops the agent from starting; run `install` again.
- The calibration window is asked to stay in front of others (`WND_PROP_TOPMOST`); it did for the user, but not every OpenCV build on macOS honours it.
- On macOS another app using the camera (Photo Booth, a video call) does not take it from the companion: both get pictures. Switching off the companion's camera permission does not stop a camera already open either; it takes effect the next time the companion opens it (after a pause, or a new session). To see C2's notice live, switch the permission off, then pause and resume.
- The calibration is not tied to a camera. After moving the camera or switching to another, the user has to calibrate again; nothing detects it.
- The looking-away margin, confidence curve, minimum zone size and default angles, the phone thresholds and the crops are guesses until C1d measures them.
- A phone held low, in the lap, is below a laptop camera's view and is not seen. A hand gripping a phone is sometimes missed, and then the phone does not count.
- Operating-system notifications cannot count down, so the companion sends a reminder 10 seconds before the time stops counting instead.

## Working in this repository

- **Run every test:** `scripts/test-all.sh` (backend, frontend, extension, and the companion's unittest suite). `FOCUSQUEST_E2E=1 scripts/test-all.sh` also runs the Playwright end-to-end suite; Claude runs it outside the sandbox.
- **Restart the backend after pulling:** schema changes are Flyway migrations and run at startup. The latest is `V16`.
- **Reload the web app after changing a Pinia store.** The dev server swaps components in place but keeps stores already created, so a page left open calls the old store (the user saw "action is not a function" after C3b).
- **Commit only your own files.** Another Claude session has worked in this folder at the same time. Check `git status`, stage files by name, and do not sweep in someone else's unfinished work.
- **Commits:** the user reviews each step, then asks for the commit. Each step is one commit, with a message explaining the why, ending with the co-author line the session is given.
- **Try the companion:** backend (`cd backend && ./gradlew bootRun`) and web app (`cd frontend && npm run dev`) running. The user's companion already runs in the background: `cd vision && .venv/bin/focusquest-companion status` says so, and its log is `~/Library/Logs/FocusQuest/companion.log`. Calibrate from Settings. For a preview window, stop the background one first (`uninstall`, later `install` again) and run `run --camera --preview`; for a scripted timeline, `run --script "present 60s, phone 90s"`. Restart the backend after changing `application.yml`.
