# Camera verification: where the work stands

This note is for picking the work up again, for example in a new Claude session. It says what is built, what comes next, and where the details live. The rules themselves are in the requirements specification (section 21, camera verification; section 19, privacy). How they are built is in the technical architecture.

Phase A closed out the MVP. Phase B built everything the camera needs before any computer vision was written, so the vision work only has to replace a fake detector with a real one. Phase C is that real detector, one signal at a time.

## Start here

All three camera signals now work with a real webcam: away, looking away and a phone in hand. The user tried them live and they work, but wants them **more sensitive**: looking away should count sooner, and a phone should count even when only half of it is in view, as when the laptop stands further back during writing. That is written up under "Future work: sensitivity". The next step is **C1d: accuracy**, under "Next step", which measures before tuning. Ask the user which of the two to do first. Before starting either:

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
| C1c | Phone in hand: EfficientDet-Lite0's "cell phone" overlapping a hand from the Hand Landmarker, with a rescaled confidence | The commit with this note; tried live, works |
| **C1d** | **Label mode, accuracy measurements, smoothing if needed, tuning** | **Next** |
| C1e | Sensitivity: looking away sooner, half-visible phones (see "Future work: sensitivity") | Asked for by the user; can come before or with C1d |
| C2 | Say in the web app when the camera cannot be used (see "Known limits") | Any time; small |

Another session also changed website blocking in `3d6a796`: sites are now blocked every day until the daily target is reached, and throughout any running session.

## Decisions the user has made

- **Away warning after 90 seconds** (was 3 minutes). Phone stays at 20 seconds, looking away at 60, the grace period at 60. Set in `focusquest.camera.warning-after` in `backend/src/main/resources/application.yml`, and in requirements §21.
- **The laptop's built-in camera** by default, not the iPhone (Continuity Camera). `--camera` picks it by name; see "The real detector" below.
- **Off task never pauses the session.** Time stops counting after the grace period and counts again when the user is back; the session keeps running. Automatic pausing was offered and not taken up.
- **Camera profiles are defaults only**; users cannot edit them yet (requirements §22, #17).
- **BlazeFace stays for presence; the Face Landmarker is added for head pose only.** The landmarker gives no per-face score, and the graded away confidence (1 minus the face score) is wanted for C1d's accuracy measurements.
- **A face lost just after a turn away is looking away**, not away, until a face is found again (within 2 seconds of the last turned-away look).
- **Phone confidence is rescaled in the companion**, not by lowering the backend's minimum: the detector scores held phones at only 0.3 to 0.8, so a phone overlapping a hand is reported from 0.7 (at a score of 0.25) to 1 (at 0.6). The overlap with a hand is what makes it believable.
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
- **C1b, looking away:** where the profile checks `LOOKING_AWAY`, `MediaPipePoses` runs the Face Landmarker (`face_landmarker.task`, pinned) and `pose_from_matrix` turns its facial transformation matrix into yaw (positive towards the picture's right) and pitch (positive down). The matrix's camera space is x right, y up, z towards the camera; this was checked by shifting a sample image. `calibration.py` holds the rule: beyond `MARGIN_DEGREES` (10) outside every zone of the work area (screen; screen or desk) is looking away, with confidence `min(1, 0.5 + 0.5·beyond/15) × (0.5 + 0.5·face score)`. `focusquest-companion calibrate` records 5 seconds at the screen, then 5 at the desk, trims 5 % at each end and saves only the angles in `calibration.json` beside the pairing file; without it, `calibration.DEFAULT` is used and `run` says so. `--preview` shows the angles and zones.
- **C1c, phone in hand:** where the profile checks `PHONE`, `MediaPipePhones` runs EfficientDet-Lite0 (float16, pinned) for COCO's "cell phone" class only. Only once a phone scores `PHONE_FROM` (0.25) or more does `MediaPipeHands` run the Hand Landmarker, at detection and presence thresholds of 0.3 rather than 0.5: a hand gripping a phone shows little palm and was missed at 0.5. `phones.py` holds the rule: the surest phone whose box overlaps a hand's landmark box is `PHONE`. On 5 stock photos of held phones and 4 desk scenes it found 3 of the held phones (0.75 to 0.95 after rescaling) and nothing on the desks, including two with a phone lying there. The 2 it missed were a blurred phone in the background and a phone the detector barely saw. All four models together take 13 to 25 ms a frame. `--preview` outlines phones (red when in a hand) and hands.
- `cameras.py` picks the camera. OpenCV numbers macOS cameras in the order of their unique IDs (it sorts them so, in `cap_avfoundation_mac.mm`). `system_profiler SPCameraDataType -json` gives each camera's name and unique ID, so the program finds the built-in camera by name and looks its number up at each opening. `focusquest-companion cameras` lists them.

**Things to know about MediaPipe:**
- Install with `.venv/bin/pip install -e '.[camera]'` in `vision/`. The unit tests need neither OpenCV nor MediaPipe: camera code is tested with fakes.
- Only the Tasks API exists (`mediapipe.tasks.python.vision`); the old `mp.solutions` is gone. Each task needs a model file, which does not ship with the package.
- **1.0.1 aborts on macOS** as soon as a detector starts (in its Metal helper, even with the CPU delegate), so the `camera` extra pins `mediapipe<1`. 0.10.35 works. Check any new model against 0.10.x.
- When Claude ran MediaPipe from its shell, it ran outside the sandbox.
- Check a detector without the webcam by feeding it images. MediaPipe's sample portrait, `https://storage.googleapis.com/mediapipe-assets/portrait.jpg`, gives one face at 0.92. Do not commit third-party images.
- Opening the webcam turns on the user's camera. Leave live tests to the user, with `--preview`.

## Next step: C1d, accuracy

**Goal.** Know how often each signal is right, and tune from that rather than from guesses. This is the research question in requirements §21.

**Suggested approach** (confirm with the user first):
1. **Label mode.** A `label` command runs the camera detector for a chosen category without a session. The user presses a key to say what they are doing (on task, away, phone, looking away), and the program logs, once a second, its detections next to the label, as numbers only, to a CSV file the user owns. Frames may never be saved (§19).
2. **Measure.** Per signal, per second: how often it was reported when the label said so, and when it did not. Then per episode: would it have warned at the profile's timings? Disputed episodes from the data export give a second, real-use source.
3. **Smoothing, if the measurements call for it.** The backend already treats gaps of up to 5 seconds as flicker. Add a majority vote over a second or two in the companion only if single missed frames still break episodes.
4. **Tune** `focusquest.camera.*`, the looking-away margin and curve in `calibration.py`, and the phone thresholds in `phones.py`.
5. Training a custom model (PyTorch) only if the pretrained ones fall short. That needs saved images, which needs its own consent decision.

**Done when:** the user has labelled at least a few minutes of each signal, the measured rates are written down (in this note or the technical architecture), any tuning follows from them, and `scripts/test-all.sh` passes.

## Future work: sensitivity

The user tried C1b and C1c live: both work, but both are too forgiving. Each item below names where the number lives, so it can be changed, and says how to check the change without the webcam. Label mode (C1d) would measure both properly; a first adjustment can come before it.

**Looking away should count sooner.**
- Today a head counts as looking away only beyond `MARGIN_DEGREES` (10) outside the calibrated zone, and its confidence reaches the backend's 0.7 only about 7 degrees further (`CERTAIN_AFTER_DEGREES`, 15, and the curve in `Calibration.looking_away`, `calibration.py`). For a clearly seen face that is about 17 degrees outside the zone before anything is reported.
- Options, smallest first: a smaller margin (for example 5); a curve that starts at 0.7 at the margin rather than 0.5, as `phones.confidence` does; smaller default zones in `calibration.DEFAULT` for uncalibrated use. Calibration already takes in the whole screen, so a smaller margin mainly cuts the extra tolerance.
- The warning time (60 seconds, `focusquest.camera.warning-after` in `application.yml`) is a separate knob; the user asked for sensitivity, not a shorter warning. Ask before changing it.
- Check: the unit tests in `test_calibration.py` use made-up angles; adjust them with the numbers. Then a live try with `--preview`, which shows the angles and the zones.

**A phone should count when only half of it is visible**, especially in writing categories, where the laptop often stands further back, so the phone is small and often cut off at the bottom edge of the picture.
- Why it is missed today: EfficientDet-Lite0 looks at a 320×320 copy of the whole frame, so a small or cut-off phone becomes a few pixels and scores below `PHONE_FROM` (0.25, `phones.py`). The hand must also be found (Hand Landmarker at 0.3, `MediaPipeHands` in `camera.py`), and the boxes must overlap.
- The frame size is not set: `Webcam` takes OpenCV's default for the camera. Check what it is (`cv2.CAP_PROP_FRAME_WIDTH`); a larger frame helps only if the detector also sees more of it (next point).
- Options:
  - Look again at the lower half of the frame, where a phone in hand usually is, as a second, enlarged image (tiling). This roughly doubles the detector's time, about 12 ms more, once a second.
  - Let a phone near a hand count, not only one overlapping it: grow the hand's box by a margin before the overlap test (`phones.overlaps` and `phones.held`).
  - Lower `PHONE_FROM` for a phone that touches the frame's edge, where being cut off explains a low score.
  - EfficientDet-Lite2 (448×448 input) sees small objects better, at about 31 ms a frame; on the sample photos it caught fewer held phones overall (2 of 5 against 3), so measure first.
- Check without the webcam: crop and shrink the sample photos so the phone is small or cut off at the bottom edge, as in "The real detector" above (do not commit third-party images). The unit tests in `test_phones.py` and `test_camera.py` use made-up boxes.
- A phone held low in the lap stays out of view whatever the model does; say so rather than guess.

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
- A user who turns away and then gets up within 2 seconds is reported as looking away until they are back, not as away. Both warn; looking away warns sooner (60 s against 90 s).
- The calibration is not tied to a camera. After moving the camera or switching to another, the user has to calibrate again; nothing detects it.
- The looking-away margin, confidence curve and default angles, and the phone thresholds, are guesses until C1d measures them.
- A phone held low, in the lap, is below a laptop camera's view and is not seen. A hand gripping a phone is sometimes missed, and then the phone does not count.
- While the camera cannot be used, the companion still reports, so the web app shows the session as checked. Nothing is observed, so nothing is subtracted, but the spec says the web app should say it is not being verified. This needs a camera-unavailable flag in the heartbeat and an off-task state for it (C2).
- Operating-system notifications cannot count down, so the companion sends a reminder 10 seconds before the time stops counting instead.

## Working in this repository

- **Run every test:** `scripts/test-all.sh` (backend, frontend, extension, and the companion's unittest suite). `FOCUSQUEST_E2E=1 scripts/test-all.sh` also runs the Playwright end-to-end suite.
- **Restart the backend after pulling:** schema changes are Flyway migrations and run at startup. The latest is `V14`.
- **Commit only your own files.** Another Claude session has worked in this folder at the same time. Check `git status`, stage files by name, and do not sweep in someone else's unfinished work.
- **Commits:** the user reviews each step, then asks for the commit. Each step is one commit, with a message explaining the why, ending with the co-author line the session is given.
- **Try the companion:** backend (`cd backend && ./gradlew bootRun`) and web app (`cd frontend && npm run dev`) running; in Settings turn camera verification on and pair; then `cd vision && .venv/bin/focusquest-companion pair`, `calibrate --preview`, and `run --camera --preview` (or `run --script ...`). Restart the backend after changing `application.yml`.
