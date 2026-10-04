# Camera verification: where the work stands

This note is for picking the work up again, for example in a new Claude session. It says what is built, what comes next, and where the details live. The rules themselves are in the requirements specification (section 21, camera verification; section 19, privacy). How they are built is in the technical architecture.

Phase A closed out the MVP. Phase B built everything the camera needs before any computer vision was written, so the vision work only has to replace a fake detector with a real one. Phase C is that real detector, one signal at a time.

## Start here

All three camera signals now work with a real webcam: away, looking away and a phone in hand. After **C1e** (under "C1e: sensitivity") they are more sensitive and warn sooner; the user tried it live and it looks a lot better. The user wants neither to start the companion nor to calibrate it from a terminal ("Next step: C3, the companion without the terminal"). **C3a** is built and tried live: `install` runs it in the background through a launch agent, and macOS asked for the camera permission as hoped. **C3b**, calibration from Settings, is built and tried live. What is left of C3 is under "Later" (a menu bar app, pairing from the browser); otherwise the next step is C1d. C1d (accuracy) comes after C3. Before starting:

1. Read this note, then `vision/README.md` (how the companion program runs and is built), then requirements §21 (the camera rules) and §19 (privacy).
2. Run `scripts/test-all.sh` to see everything pass before changing anything.
3. Ask the user whether they want the step as planned below. They review each step before it is committed.

## Status

| Step | What | Commit |
| --- | --- | --- |
| A1 | Open decisions in requirements §22 settled: XP, gems, penalty, completion, categories and more | `96c670f` |
| A2 | Streak freezes: 10 gems each, at most 2, daily only, only for whole gaps, bridge without counting (requirements §13) | `96c670f` |
| A3 | Complete export, and restore from Settings; tests for retried requests and for restarts | `272d453` |
| A4 | Wider end-to-end coverage | **Open.** 6 Playwright tests: 4 for sessions and blocking, 2 for the camera. Not covered end to end: the login form, streak progress, interruption |
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
| C1d | Label mode, accuracy measurements, smoothing if needed, tuning | After C3 |
| C1e | Sensitivity: warnings after 5 s of phone and 15 s of looking away (grace stays 60 s); looking away from 5° beyond the zone, at full confidence; phones and hands also looked for in enlarged crops of the lower frame (see "C1e: sensitivity") | The commit with this note; tried live, a lot better |
| C3a | The companion starts by itself: `install` writes a macOS launch agent; `--background` waits to be paired. Calibrated zones at least 20° by 14° | The commit with this note; tried live, works |
| C3b | Calibration started from Settings, guided in the camera window; Settings shows when it was calibrated or why it failed | The commit with this note; tried live, works |
| C2 | Say in the web app when the camera cannot be used (see "Known limits") | Any time; small |

Another session also changed website blocking in `3d6a796`: sites are now blocked every day until the daily target is reached, and throughout any running session.

## Decisions the user has made

- **Warnings come early, as a heads-up; the grace period stays at 60 seconds.** Away after 90 seconds (was 3 minutes), a phone in hand after 5 (was 20), looking away after 15 (was 60). The user wants to know at once, and still have a minute to put the phone away before time stops counting. Set in `focusquest.camera.warning-after` in `backend/src/main/resources/application.yml`, and in requirements §21.
- **The laptop's built-in camera** by default, not the iPhone (Continuity Camera). `--camera` picks it by name; see "The real detector" below.
- **Off task never pauses the session.** Time stops counting after the grace period and counts again when the user is back; the session keeps running. Automatic pausing was offered and not taken up.
- **Camera profiles are defaults only**; users cannot edit them yet (requirements §22, #17).
- **BlazeFace stays for presence; the Face Landmarker is added for head pose only.** The landmarker gives no per-face score, and the graded away confidence (1 minus the face score) is wanted for C1d's accuracy measurements.
- **A face lost just after a turn away is looking away**, not away, until a face is found again (within 2 seconds of the last turned-away look).
- **Phone confidence is rescaled in the companion**, not by lowering the backend's minimum: the detector scores held phones at only 0.3 to 0.8, so a phone overlapping a hand is reported from 0.7 (at a score of 0.25) to 1 (at 0.6). The overlap with a hand is what makes it believable.
- **Sensitivity (C1e) before accuracy (C1d)**: a first adjustment from what the user saw live, then measure.
- **No terminal for everyday use (C3) before C1d.** The companion runs natively on the Mac, not in a container: Docker on macOS cannot reach the camera, open windows or show notifications. A launch agent starts it, and Settings starts calibration. Detection in the browser was considered and not taken: browsers throttle background tabs.
- **EfficientDet-Lite0, float16** for phones, over Lite2: on sample photos it caught 3 of 5 held phones against Lite2's 2, at about 12 ms a frame against 31.

## How the pieces fit

- **Backend** (Spring Boot, `backend/`, package `vision`): consent, profiles, pairing, the companion endpoints, and all the rules. From observations it works out episodes, warnings and subtracted time (`OffTaskCalculator`, `OffTaskService`). The companion never decides what is off task; it only reports what it sees.
- **Web app** (Vue, `frontend/`): Settings (`CameraVerificationCard`: consent, pairing code, what each category checks), the "Verify with camera" switch, the warning banner (`OffTaskBanner`), net focused time and history.
- **Companion program** (Python, `vision/`): paired with a `fqc_` code, it reports every 2 seconds and looks once a second. It opens the camera only while the backend's answer says `cameraOn`. It sends observations: stretches of one signal (`AWAY`, `PHONE`, `LOOKING_AWAY`) with a confidence and a start and end, stamped in the backend's clock (`serverTime`). It turns the backend's warnings into system notifications. `vision/README.md` has the details and a table of its files.
- **End-to-end** (`e2e/`, Playwright): `e2e/tests/camera.spec.ts` runs the companion program for real with a scripted detector.

## The real detector (C1)

`CameraDetector` in `vision/src/focusquest_companion/camera.py`, behind the `Detector` interface (`open`, `detect(now, profile)`, `close`) in `detector.py`, chosen with `run --camera`. `ScriptedDetector` implements the same interface for tests and demos (`run --script "present 60s, phone 90s"`).

**Built so far:**
- The webcam (`Webcam`) is read on a background thread that keeps only the latest frame, in memory.
- `MediaPipeFaces` runs MediaPipe's BlazeFace (short range). No face at 0.5 or above is `AWAY`, with confidence 1 minus the best face's score.
- Models are downloaded once to `~/.cache/focusquest/models` and checked against a pinned SHA-256 (`models.py`).
- A camera that cannot open, or stops sending frames, raises `DetectorUnavailable`. The companion says so once and retries at each report.
- `--preview` shows the frame, the face box and the result in a window.
- **C1b, looking away:** where the profile checks `LOOKING_AWAY`, `MediaPipePoses` runs the Face Landmarker (`face_landmarker.task`, pinned) and `pose_from_matrix` turns its facial transformation matrix into yaw (positive towards the picture's right) and pitch (positive down). The matrix's camera space is x right, y up, z towards the camera; this was checked by shifting a sample image. `calibration.py` holds the rule: beyond `MARGIN_DEGREES` (5 since C1e; it was 10) outside every zone of the work area (screen; screen or desk) is looking away, with confidence `(0.7 + 0.3·min(1, beyond/15)) × min(1, face score/0.7)`. `focusquest-companion calibrate` records 5 seconds at the screen, then 5 at the desk, trims 5 % at each end and saves only the angles in `calibration.json` beside the pairing file; without it, `calibration.DEFAULT` is used and `run` says so. `--preview` shows the angles and zones.
- **C1c, phone in hand:** where the profile checks `PHONE`, `MediaPipePhones` runs EfficientDet-Lite0 (float16, pinned) for COCO's "cell phone" class only. Only once a phone scores `PHONE_FROM` (0.25) or more does `MediaPipeHands` run the Hand Landmarker, at detection and presence thresholds of 0.3 rather than 0.5: a hand gripping a phone shows little palm and was missed at 0.5. `phones.py` holds the rule: the surest phone whose box overlaps a hand's landmark box is `PHONE`. Since C1e, both models also look at two enlarged crops of the lower frame (see "C1e: sensitivity"). On 5 stock photos of held phones and 4 desk scenes it found 3 of the held phones (0.75 to 0.95 after rescaling) and nothing on the desks, including two with a phone lying there. The 2 it missed were a blurred phone in the background and a phone the detector barely saw. All four models together take 13 to 25 ms a frame. `--preview` outlines phones (red when in a hand) and hands.
- `cameras.py` picks the camera. OpenCV numbers macOS cameras in the order of their unique IDs (it sorts them so, in `cap_avfoundation_mac.mm`). `system_profiler SPCameraDataType -json` gives each camera's name and unique ID, so the program finds the built-in camera by name and looks its number up at each opening. `focusquest-companion cameras` lists them.

**Things to know about MediaPipe:**
- Install with `.venv/bin/pip install -e '.[camera]'` in `vision/`. The unit tests need neither OpenCV nor MediaPipe: camera code is tested with fakes.
- Only the Tasks API exists (`mediapipe.tasks.python.vision`); the old `mp.solutions` is gone. Each task needs a model file, which does not ship with the package.
- **1.0.1 aborts on macOS** as soon as a detector starts (in its Metal helper, even with the CPU delegate), so the `camera` extra pins `mediapipe<1`. 0.10.35 works. Check any new model against 0.10.x.
- When Claude ran MediaPipe from its shell, it ran outside the sandbox.
- Check a detector without the webcam by feeding it images. MediaPipe's sample portrait, `https://storage.googleapis.com/mediapipe-assets/portrait.jpg`, gives one face at 0.92. Do not commit third-party images.
- Opening the webcam turns on the user's camera. Leave live tests to the user, with `--preview`.

## Next step: C3, the companion without the terminal

The user should not need a terminal for everyday use: not to start the companion, not to calibrate it. Agreed with the user, in two steps, each reviewed and committed on its own.

**C3a: the companion starts by itself** (companion only).
- `focusquest-companion install` does the one-time setup: pairs if not paired yet, then writes a launch agent (`~/Library/LaunchAgents`) that runs `run --camera` at login and again if it stops, with its output in `~/Library/Logs/FocusQuest/companion.log`. `uninstall` removes it.
- Unpaired, `run` waits for a pairing rather than exiting, so the launch agent does not restart it in a loop.
- **Built** (`service.py`, `cli.py`): `install` pairs if needed, downloads the models, writes `~/Library/LaunchAgents/com.focusquest.companion.plist` (`RunAtLoad`, `KeepAlive`, restart after 30 s, Aqua sessions only) running `<venv python> -m focusquest_companion --config <path> run --camera --background`, and bootstraps it with `launchctl` into `gui/<uid>`. `uninstall` boots it out and deletes it; `status` reads `launchctl print`. `--background` waits for a pairing (checked every 5 s), waits again after a refused token, and puts the time on each log line; a terminal `run` without it behaves as before (the end-to-end tests match its lines from the start), and warns when the agent also runs. Tried live: the user installed it, macOS asked for the camera permission, and warnings arrived as notifications.
- Risk to check first, live with the user: macOS may not show the camera permission prompt to a program started in the background. If it does not, wrap the companion in a small `.app` (with `NSCameraUsageDescription`), which the menu bar app would need anyway.

**C3b: calibration from Settings** (backend, web app, companion).
- A "Calibrate" button in Settings, while the companion is connected and no camera-verified session runs. The backend records the request; the companion's next answer says to calibrate.
- The companion opens its camera window and guides with countdowns drawn on it (screen, then desk) in place of "Press Enter", then tells the backend whether it worked. Only the time and the outcome go to the backend; the angles stay on the computer.
- Settings shows when it was calibrated, "Follow the steps in the camera window", or why it failed, and stops telling the user to run `calibrate`.
- **Built.** Backend: `POST /api/me/companion-calibration` (409 unless on, paired, connected and no camera-verified session ACTIVE), `calibrate` in the companion's answer, `POST /api/companion/calibration` (`STARTED`, `CALIBRATED` with `calibratedAt`, `FAILED` with `message`), columns on `companion_credentials` (V15), and `CompanionCalibration.of` working out the state, where a request not picked up or a calibration not finished within 2 minutes is `FAILED`. Companion: `Companion` calls a `Calibrator` when asked, after saying `STARTED`, and on connecting says when its saved calibration was made (the file's time); a scripted companion says it has nothing to calibrate. `CameraCalibrator` (`camera.py`) opens its own `Webcam`, runs `guided_calibration` (a 3-second "Get ready" countdown, then 5 seconds kept, for the screen and then the desk, drawn on a window asked to stay in front, `WND_PROP_TOPMOST`), saves, and hands the angles to the running `CameraDetector` (`use_calibration`). The terminal `calibrate` uses the same flow, with no "Press Enter"; its `--preview` is still accepted and does nothing. Web app: a "Calibrate" button and status line under the companion's status, followed every 2 seconds while under way. End-to-end: the scripted companion answers "Calibrate" with its failure.
- While calibrating (about 16 seconds) the companion does not report. That stays well within the 30 seconds it counts as connected.
- Tried live: it works from Settings with the background program. At the user's request the window's text was made larger and easier to read: it grows with the camera picture (`Preview.annotate`, sizes meant for 1280 pixels across), shrinks only where a line would not fit, and sits on a darkened band, the instruction in yellow and the rest in white.

**Later:** a menu bar app (an icon showing whether it watches, a preview on demand, the camera permission in FocusQuest's name), and pairing from the browser, which needs the app bundle's URL scheme or a local listener with its own security questions.

## After C3: C1d, accuracy

**Goal.** Know how often each signal is right, and tune from that rather than from guesses. This is the research question in requirements §21.

**Suggested approach** (confirm with the user first):
1. **Label mode.** A `label` command runs the camera detector for a chosen category without a session. The user presses a key to say what they are doing (on task, away, phone, looking away), and the program logs, once a second, its detections next to the label, as numbers only, to a CSV file the user owns. Frames may never be saved (§19).
2. **Measure.** Per signal, per second: how often it was reported when the label said so, and when it did not. Then per episode: would it have warned at the profile's timings? Disputed episodes from the data export give a second, real-use source.
3. **Smoothing, if the measurements call for it.** The backend already treats gaps of up to 5 seconds as flicker. Add a majority vote over a second or two in the companion only if single missed frames still break episodes.
4. **Tune** `focusquest.camera.*`, the looking-away margin and curve in `calibration.py`, and the phone thresholds in `phones.py`.
5. Training a custom model (PyTorch) only if the pretrained ones fall short. That needs saved images, which needs its own consent decision.

**Done when:** the user has labelled at least a few minutes of each signal, the measured rates are written down (in this note or the technical architecture), any tuning follows from them, and `scripts/test-all.sh` passes.

## C1e: sensitivity (done)

The user tried C1b and C1c live: both worked, but both were too forgiving. C1e is a first adjustment, made before C1d measures anything. The user tried it live after calibrating again: a lot better.

**Looking away counts sooner.**
- Before: a head counted only beyond `MARGIN_DEGREES` (10) outside the calibrated zone, and its confidence reached the backend's 0.7 only about 7 degrees further. In all, that was about 17 degrees.
- Now: the margin is 5, and the confidence is `LEAST_CONFIDENCE` (0.7) just past the margin, growing to 1 over `CERTAIN_AFTER_DEGREES` (15). A face found below `CLEAR_FACE_FROM` (0.7) lowers it in proportion. A clearly seen face counts as soon as it is 5 degrees outside the zone. All of this is in `calibration.py`.
- The user's calibrated screen zone is only about 15 degrees wide and 7 tall, so 5 degrees is a small tolerance. If reading the screen's edges or corners now counts as looking away, the first fix is to calibrate again across the whole screen; after that, a margin of 7 or 8.
- Not changed: the default zones in `calibration.DEFAULT` (the user has calibrated) The user then also asked for earlier warnings: a phone after 5 seconds, looking away after 15 (see "Decisions the user has made"). With both together, a 5-second glance at a phone, or a 15-second look 5 degrees beyond the screen, now pops a notification.
- **Found in the first live try of C3a:** after recalibrating, the user was reported looking away for minutes while working. The new calibration's screen zone was 3 degrees wide and 2.6 tall (turned +1 to +4, looking 18 to 20 degrees up): calibration measures the head, and reading across a screen mostly moves the eyes. With the 5-degree margin nearly every pose was off task, and the 60-second countdown ran on, correctly, from what was reported. Zones are now at least `MIN_YAW_SPAN` (20) by `MIN_PITCH_SPAN` (14) degrees around their middle (`Zone.widened`, also applied when loading older calibrations), and `CameraDetector` logs the head's angles and the work area when a look away starts, so the background log shows why.
- Unknown: BlazeFace's score for a turned head live. If it drops below 0.7, the confidence drops with it. The preview shows the face's score.

**A phone counts when it is small or half out of the picture.**
- Why it was missed: EfficientDet-Lite0 looks at a 320×320 copy of the whole frame, so a small or cut-off phone is a few pixels and scores below `PHONE_FROM` (0.25). The hand was missed for the same reason.
- Now: `MediaPipePhones` and `MediaPipeHands` look at the whole frame and again at two enlarged crops of its lower part (`phones.closer_looks`: lower left and lower right, each 60 % of the width and height, overlapping in the middle). `phones.distinct` drops a phone found again in a crop. A phone and a hand still have to overlap.
- Measured on the 5 held-phone and 4 desk photos (in a scratchpad, not committed), each also shrunk to a half and to a third inside a grey border ("far"), and for the found phones shifted until the phone is half below the bottom edge ("cut", and "far" plus "cut"). Of the 15 versions of the three held phones the detector can see at all, it now finds 9; before, 5. It finds none of the desk phones. The one false positive is a laptop under a typing hand at a third of its size, which the old rule also counted; a built-in laptop camera does not see its own laptop.
- What the crops added: phones in crops brought 3 more, and hands in crops 1 more. Two other ideas were tried and left out because they caught nothing more and added a false positive: counting a phone *near* a hand (the hand's box grown by a quarter), and a lower threshold (0.15) for a phone touching the frame's edge.
- Cost: up to about 70 ms per look for phones and hands together (before, about 13 to 25 for all four models), once a second.
- Still missed: phones whose hand is out of the picture or not found, and phones a third of their size. A phone held low in the lap stays out of view whatever the model does.
- `Webcam` now logs the frame size when it opens ("Using …, 1280 by 720 pixels"). OpenCV's default is used. If it is small (640 by 480), asking for 1280 by 720 would give the crops more to see.

## After that

- **C2, camera unavailable.** See "Known limits".

## Where the decisions live

- **Camera rules** (off-task signals, profiles, warning then subtraction, when time is settled, disputes, failure behaviour): requirements specification §21.
- **Privacy:** §19.
- **Freezes and streak loss:** §13.
- **Open questions and their answers:** §22. #5, on how long an abandoned session's websites stay blocked, was settled by the daily-blocking change.
- **Off-task time model, companion endpoints, token scoping and the web app pieces:** technical architecture. Search for "Off-task time model", "Pairing the companion program", "Off-task warnings in the web app" and "Camera verification".
- **How the companion program works and runs:** `vision/README.md`.

## Known limits

- Where off-task time crosses midnight or the start of the week, the day split can be off by up to a second.
- Observations that arrive after their stretch of time was settled change nothing already settled.
- Users cannot edit the camera profiles yet; this was decided (requirements §22, #17).
- When the camera closes (pause, end), observations from its last 2 seconds that were not reported yet are dropped.
- A user who turns away and then gets up within 2 seconds is reported as looking away until they are back, not as away. Both warn; looking away warns sooner (15 s against 90 s).
- The launch agent points at `vision/.venv`'s Python. Moving or recreating the venv, or a Homebrew Python upgrade that breaks it, stops the agent from starting; run `install` again.
- The calibration window is asked to stay in front of others (`WND_PROP_TOPMOST`); not every OpenCV build on macOS honours it. If it opens behind, the user has to find it.
- The calibration is not tied to a camera. After moving the camera or switching to another, the user has to calibrate again; nothing detects it.
- The looking-away margin, confidence curve and default angles, the phone thresholds and the crops are guesses until C1d measures them. C1e's numbers come from what the user saw live and from shrunk and cut stock photos, not from webcam footage.
- A phone held low, in the lap, is below a laptop camera's view and is not seen. A hand gripping a phone is sometimes missed, and then the phone does not count.
- While the camera cannot be used, the companion still reports, so the web app shows the session as checked. Nothing is observed, so nothing is subtracted, but the spec says the web app should say it is not being verified. This needs a camera-unavailable flag in the heartbeat and an off-task state for it (C2).
- Operating-system notifications cannot count down, so the companion sends a reminder 10 seconds before the time stops counting instead.

## Working in this repository

- **Run every test:** `scripts/test-all.sh` (backend, frontend, extension, and the companion's unittest suite). `FOCUSQUEST_E2E=1 scripts/test-all.sh` also runs the Playwright end-to-end suite.
- **Restart the backend after pulling:** schema changes are Flyway migrations and run at startup. The latest is `V14`.
- **Commit only your own files.** Another Claude session has worked in this folder at the same time. Check `git status`, stage files by name, and do not sweep in someone else's unfinished work.
- **Commits:** the user reviews each step, then asks for the commit. Each step is one commit, with a message explaining the why, ending with the co-author line the session is given.
- **Try the companion:** backend (`cd backend && ./gradlew bootRun`) and web app (`cd frontend && npm run dev`) running; in Settings turn camera verification on and pair; then `cd vision && .venv/bin/focusquest-companion pair`, `calibrate --preview`, and `run --camera --preview` (or `run --script ...`). Restart the backend after changing `application.yml`.
