# Camera verification: where the work stands

This note is for picking the work up again, for example in a new Claude session. It says what is built, what comes next, and where the details live. The rules themselves are in the requirements specification (section 21, camera verification; section 19, privacy). How they are built is in the technical architecture.

Phase A closed out the MVP. Phase B built everything the camera needs before any computer vision was written, so the vision work only has to replace a fake detector with a real one. Phase C is that real detector, one signal at a time.

## Start here

The next step is **C1c: phone in hand**, described below under "Next step". Before starting it:

1. Read this note, then `vision/README.md` (how the companion program runs and is built), then requirements §21 (the camera rules) and §19 (privacy).
2. Run `scripts/test-all.sh` to see everything pass before changing anything.
3. Ask the user whether they want C1c as planned below. They review each step before it is committed.

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
| C1b | Looking away: Face Landmarker head pose against the work area, `calibrate`, a lost face after a turn stays looking away | Committed after this note; not yet confirmed live |
| **C1c** | **Phone in hand** | **Next** |
| C1d | Smoothing, label mode, accuracy measurements, tuning | |
| C2 | Say in the web app when the camera cannot be used (see "Known limits") | Any time; small |

Another session also changed website blocking in `3d6a796`: sites are now blocked every day until the daily target is reached, and throughout any running session.

## Decisions the user has made

- **Away warning after 90 seconds** (was 3 minutes). Phone stays at 20 seconds, looking away at 60, the grace period at 60. Set in `focusquest.camera.warning-after` in `backend/src/main/resources/application.yml`, and in requirements §21.
- **The laptop's built-in camera** by default, not the iPhone (Continuity Camera). `--camera` picks it by name; see "The real detector" below.
- **Off task never pauses the session.** Time stops counting after the grace period and counts again when the user is back; the session keeps running. Automatic pausing was offered and not taken up.
- **Camera profiles are defaults only**; users cannot edit them yet (requirements §22, #17).
- **BlazeFace stays for presence; the Face Landmarker is added for head pose only.** The landmarker gives no per-face score, and the graded away confidence (1 minus the face score) is wanted for C1d's accuracy measurements.
- **A face lost just after a turn away is looking away**, not away, until a face is found again (within 2 seconds of the last turned-away look).

## How the pieces fit

- **Backend** (Spring Boot, `backend/`, package `vision`): consent, profiles, pairing, the companion endpoints, and all the rules. From observations it works out episodes, warnings and subtracted time (`OffTaskCalculator`, `OffTaskService`). The companion never decides what is off task; it only reports what it sees.
- **Web app** (Vue, `frontend/`): Settings (`CameraVerificationCard`: consent, pairing code, what each category checks), the "Verify with camera" switch, the warning banner (`OffTaskBanner`), net focused time and history.
- **Companion program** (Python, `vision/`): paired with a `fqc_` code, it reports every 2 seconds and looks once a second. It opens the camera only while the backend's answer says `cameraOn`. It sends observations: stretches of one signal (`AWAY`, `PHONE`, `LOOKING_AWAY`) with a confidence and a start and end, stamped in the backend's clock (`serverTime`). It turns the backend's warnings into system notifications. `vision/README.md` has the details and a table of its files.
- **End-to-end** (`e2e/`, Playwright): `e2e/tests/camera.spec.ts` runs the companion program for real with a scripted detector.

## The real detector (C1)

`CameraDetector` in `vision/src/focusquest_companion/camera.py`, behind the `Detector` interface (`open`, `detect(now, profile)`, `close`) in `detector.py`, chosen with `run --camera`. `ScriptedDetector` implements the same interface for tests and demos (`run --script "present 60s, phone 90s"`).

**Built so far (C1a):**
- The webcam (`Webcam`) is read on a background thread that keeps only the latest frame, in memory.
- `MediaPipeFaces` runs MediaPipe's BlazeFace (short range). No face at 0.5 or above is `AWAY`, with confidence 1 minus the best face's score.
- Models are downloaded once to `~/.cache/focusquest/models` and checked against a pinned SHA-256 (`models.py`).
- A camera that cannot open, or stops sending frames, raises `DetectorUnavailable`. The companion says so once and retries at each report.
- `--preview` shows the frame, the face box and the result in a window.
- **C1b, looking away:** where the profile checks `LOOKING_AWAY`, `MediaPipePoses` runs the Face Landmarker (`face_landmarker.task`, pinned) and `pose_from_matrix` turns its facial transformation matrix into yaw (positive towards the picture's right) and pitch (positive down). The matrix's camera space is x right, y up, z towards the camera; this was checked by shifting a sample image. `calibration.py` holds the rule: beyond `MARGIN_DEGREES` (10) outside every zone of the work area (screen; screen or desk) is looking away, with confidence `min(1, 0.5 + 0.5·beyond/15) × (0.5 + 0.5·face score)`. `focusquest-companion calibrate` records 5 seconds at the screen, then 5 at the desk, trims 5 % at each end and saves only the angles in `calibration.json` beside the pairing file; without it, `calibration.DEFAULT` is used and `run` says so. `--preview` shows the angles and zones.
- `cameras.py` picks the camera. OpenCV numbers macOS cameras in the order of their unique IDs (it sorts them so, in `cap_avfoundation_mac.mm`). `system_profiler SPCameraDataType -json` gives each camera's name and unique ID, so the program finds the built-in camera by name and looks its number up at each opening. `focusquest-companion cameras` lists them.

**Things to know about MediaPipe:**
- Install with `.venv/bin/pip install -e '.[camera]'` in `vision/`. The unit tests need neither OpenCV nor MediaPipe: camera code is tested with fakes.
- Only the Tasks API exists (`mediapipe.tasks.python.vision`); the old `mp.solutions` is gone. Each task needs a model file, which does not ship with the package.
- **1.0.1 aborts on macOS** as soon as a detector starts (in its Metal helper, even with the CPU delegate), so the `camera` extra pins `mediapipe<1`. 0.10.35 works. Check any new model against 0.10.x.
- When Claude ran MediaPipe from its shell, it ran outside the sandbox.
- Check a detector without the webcam by feeding it images. MediaPipe's sample portrait, `https://storage.googleapis.com/mediapipe-assets/portrait.jpg`, gives one face at 0.92. Do not commit third-party images.
- Opening the webcam turns on the user's camera. Leave live tests to the user, with `--preview`.

## Next step: C1c, phone in hand

**C1b still needs a live try:** `calibrate --preview`, then `run --camera --preview` in a Writing and a Coding session. Its margin and default angles may need adjusting from that.

**Goal.** Report `PHONE` when a phone is in the user's hand. A phone lying on the desk is not off task.

**Suggested approach** (confirm with the user first):
1. MediaPipe's Object Detector (EfficientDet-Lite, trained on COCO, whose classes include "cell phone") finds phones; the Hand Landmarker finds hands. A phone box overlapping a hand is `PHONE`, with the lower of the two scores. Download and pin both models as in `models.py`, and check them against MediaPipe 0.10.x.
2. Run them only where the profile checks `PHONE` (every category today), and see what it costs per frame: the detector looks once a second.
3. A laptop camera often cannot see the lap. Say so in the README and the Settings notice rather than guess.
4. Unit-test the overlap rule with made-up boxes, as `test_camera.py` does with fake faces and poses. Check the models with images, not the webcam; do not commit third-party images.

**Done when:** a phone held in view for 20 seconds warns, one lying on the desk does not, `scripts/test-all.sh` passes, the user has tried it live, and the docs (this note, `vision/README.md`, the technical architecture, the Settings notice) are updated.

## After that

- **C1d, accuracy.**
  - Smoothing: a signal counts once seen in most frames over a second or two.
  - A label mode: the user says what they are doing, and the program logs its detections next to the label, as numbers only. Frames may never be saved (§19).
  - Measure accuracy from label mode and from disputed episodes (the research question in §21).
  - Then tune `focusquest.camera.*`, and the looking-away margin and confidence in `calibration.py`.
  - Training a custom model (PyTorch) only if the pretrained ones fall short. That needs saved images, which needs its own consent decision.
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
- The looking-away margin, confidence curve and default angles are guesses until C1d measures them.
- While the camera cannot be used, the companion still reports, so the web app shows the session as checked. Nothing is observed, so nothing is subtracted, but the spec says the web app should say it is not being verified. This needs a camera-unavailable flag in the heartbeat and an off-task state for it (C2).
- Operating-system notifications cannot count down, so the companion sends a reminder 10 seconds before the time stops counting instead.

## Working in this repository

- **Run every test:** `scripts/test-all.sh` (backend, frontend, extension, and the companion's unittest suite). `FOCUSQUEST_E2E=1 scripts/test-all.sh` also runs the Playwright end-to-end suite.
- **Restart the backend after pulling:** schema changes are Flyway migrations and run at startup. The latest is `V14`.
- **Commit only your own files.** Another Claude session has worked in this folder at the same time. Check `git status`, stage files by name, and do not sweep in someone else's unfinished work.
- **Commits:** the user reviews each step, then asks for the commit. Each step is one commit, with a message explaining the why, ending with the co-author line the session is given.
- **Try the companion:** backend (`cd backend && ./gradlew bootRun`) and web app (`cd frontend && npm run dev`) running; in Settings turn camera verification on and pair; then `cd vision && .venv/bin/focusquest-companion pair`, `calibrate --preview`, and `run --camera --preview` (or `run --script ...`). Restart the backend after changing `application.yml`.
