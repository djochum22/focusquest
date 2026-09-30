# Camera verification: where the work stands

This note is for picking the work up again, for example in a new Claude session. It says what is built, what comes next, and where the details live. The rules themselves are in the requirements specification (section 21, camera verification; section 19, privacy). How they are built is in the technical architecture.

Phase A closed out the MVP. Phase B built everything the camera needs before any computer vision was written, so the vision work only has to replace a fake detector with a real one. Phase C is that real detector, one signal at a time.

## Start here

The next step is **C1b: looking away**, described below under "Next step". Before starting it:

1. Read this note, then `vision/README.md` (how the companion program runs and is built), then requirements §21 (the camera rules) and §19 (privacy).
2. Run `scripts/test-all.sh` to see everything pass before changing anything.
3. Ask the user whether they want C1b as planned below. They review each step before it is committed.

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
| **C1b** | **Looking away: head pose against the work area, with calibration** | **Next** |
| C1c | Phone in hand | |
| C1d | Smoothing, label mode, accuracy measurements, tuning | |
| C2 | Say in the web app when the camera cannot be used (see "Known limits") | Any time; small |

Another session also changed website blocking in `3d6a796`: sites are now blocked every day until the daily target is reached, and throughout any running session.

## Decisions the user has made

- **Away warning after 90 seconds** (was 3 minutes). Phone stays at 20 seconds, looking away at 60, the grace period at 60. Set in `focusquest.camera.warning-after` in `backend/src/main/resources/application.yml`, and in requirements §21.
- **The laptop's built-in camera** by default, not the iPhone (Continuity Camera). `--camera` picks it by name; see "The real detector" below.
- **Off task never pauses the session.** Time stops counting after the grace period and counts again when the user is back; the session keeps running. Automatic pausing was offered and not taken up.
- **Camera profiles are defaults only**; users cannot edit them yet (requirements §22, #17).

## How the pieces fit

- **Backend** (Spring Boot, `backend/`, package `vision`): consent, profiles, pairing, the companion endpoints, and all the rules. From observations it works out episodes, warnings and subtracted time (`OffTaskCalculator`, `OffTaskService`). The companion never decides what is off task; it only reports what it sees.
- **Web app** (Vue, `frontend/`): Settings (`CameraVerificationCard`: consent, pairing code, what each category checks), the "Verify with camera" switch, the warning banner (`OffTaskBanner`), net focused time and history.
- **Companion program** (Python, `vision/`): paired with a `fqc_` code, it reports every 2 seconds and looks once a second. It opens the camera only while the backend's answer says `cameraOn`. It sends observations: stretches of one signal (`AWAY`, `PHONE`, `LOOKING_AWAY`) with a confidence and a start and end, stamped in the backend's clock (`serverTime`). It turns the backend's warnings into system notifications. `vision/README.md` has the details and a table of its files.
- **End-to-end** (`e2e/`, Playwright): `e2e/tests/camera.spec.ts` runs the companion program for real with a scripted detector.

## The real detector (C1)

`CameraDetector` in `vision/src/focusquest_companion/camera.py`, behind the `Detector` interface (`open`, `detect`, `close`) in `detector.py`, chosen with `run --camera`. `ScriptedDetector` implements the same interface for tests and demos (`run --script "present 60s, phone 90s"`).

**Built so far (C1a):**
- The webcam (`Webcam`) is read on a background thread that keeps only the latest frame, in memory.
- `MediaPipeFaces` runs MediaPipe's BlazeFace (short range). No face at 0.5 or above is `AWAY`, with confidence 1 minus the best face's score.
- Models are downloaded once to `~/.cache/focusquest/models` and checked against a pinned SHA-256 (`models.py`).
- A camera that cannot open, or stops sending frames, raises `DetectorUnavailable`. The companion says so once and retries at each report.
- `--preview` shows the frame, the face box and the result in a window.
- `cameras.py` picks the camera. OpenCV numbers macOS cameras in the order of their unique IDs (it sorts them so, in `cap_avfoundation_mac.mm`). `system_profiler SPCameraDataType -json` gives each camera's name and unique ID, so the program finds the built-in camera by name and looks its number up at each opening. `focusquest-companion cameras` lists them.

**Things to know about MediaPipe:**
- Install with `.venv/bin/pip install -e '.[camera]'` in `vision/`. The unit tests need neither OpenCV nor MediaPipe: camera code is tested with fakes.
- Only the Tasks API exists (`mediapipe.tasks.python.vision`); the old `mp.solutions` is gone. Each task needs a model file, which does not ship with the package.
- **1.0.1 aborts on macOS** as soon as a detector starts (in its Metal helper, even with the CPU delegate), so the `camera` extra pins `mediapipe<1`. 0.10.35 works. Check any new model against 0.10.x.
- When Claude ran MediaPipe from its shell, it ran outside the sandbox.
- Check a detector without the webcam by feeding it images. MediaPipe's sample portrait, `https://storage.googleapis.com/mediapipe-assets/portrait.jpg`, gives one face at 0.92. Do not commit third-party images.
- Opening the webcam turns on the user's camera. Leave live tests to the user, with `--preview`.

## Next step: C1b, looking away

**Goal.** Report `LOOKING_AWAY` when the user's head is turned away from the category's work area.

**The work areas** come from the backend in each heartbeat (`profile.workArea`):
- `SCREEN` (coding, work, administration): the screen only.
- `SCREEN_OR_DESK` (studying, reading, writing, planning): the screen, or down at the desk. Reading or writing on paper must not count as looking away.
- `ANYWHERE` (creative work, other, task-free): looking away is not checked, and `LOOKING_AWAY` is not in the profile's checks.

`ObservationTracker` already drops any signal the profile does not check.

**Suggested approach** (confirm with the user first):
1. **Head pose.** Use MediaPipe's Face Landmarker with `output_facial_transformation_matrixes=True`; the matrix gives the head's yaw (left and right) and pitch (up and down). Download the model the same way as the face detector, pinning its SHA-256. The Face Landmarker could also replace BlazeFace for presence, with one model instead of two; decide whether that is simpler.
2. **Calibration**, because every camera sits somewhere different. A `calibrate` command asks the user to look at the screen for a few seconds, then down at the desk. It stores only the angles, never images, next to the pairing in `config.py`'s file. Without calibration, fall back to fixed angles around straight ahead and say so.
3. **The rule.** The head is outside the calibrated screen angles (plus the desk angles for `SCREEN_OR_DESK`) by more than a margin: that is `LOOKING_AWAY`. Confidence grows with how far outside it is, and with the face's score.
4. **Away versus looking away.** A head turned far enough loses the face, which today counts as `AWAY`. Consider whether a face lost just after a large turn should be `LOOKING_AWAY` instead.
5. **Pass the profile to the detector.** `detect(now)` does not receive the work area today. `Companion.look()` has the profile (`self._state.profile`), so extend the interface: for example `detect(now, profile)`, or a method called when the profile arrives.
6. **Preview and tests.** Show the angles and the calibrated ranges in `--preview`. Unit-test the rule with made-up angles, as `test_camera.py` does with fake faces.

**Done when:**
- Looking at the screen, or at paper where the desk counts, is on task.
- Turning away for 60 seconds warns.
- `scripts/test-all.sh` passes.
- The user has tried it live with `--preview`.
- Docs are updated: `vision/README.md`, this note, the technical architecture's "Camera verification" part, and the Settings notice in `CameraVerificationCard.vue` (it says only presence is checked).

## After that

- **C1c, phone in hand.** MediaPipe's Object Detector (EfficientDet-Lite, trained on COCO, whose classes include "cell phone"), combined with the Hand Landmarker: a phone box overlapping a hand is `PHONE`. A phone lying on the desk is not. A laptop camera often cannot see the lap; say so rather than guess.
- **C1d, accuracy.**
  - Smoothing: a signal counts once seen in most frames over a second or two.
  - A label mode: the user says what they are doing, and the program logs its detections next to the label, as numbers only. Frames may never be saved (§19).
  - Measure accuracy from label mode and from disputed episodes (the research question in §21).
  - Then tune `focusquest.camera.*`.
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
- A head turned far to the side can lose the face entirely, which counts as away rather than looking away (C1b, point 4).
- While the camera cannot be used, the companion still reports, so the web app shows the session as checked. Nothing is observed, so nothing is subtracted, but the spec says the web app should say it is not being verified. This needs a camera-unavailable flag in the heartbeat and an off-task state for it (C2).
- Operating-system notifications cannot count down, so the companion sends a reminder 10 seconds before the time stops counting instead.

## Working in this repository

- **Run every test:** `scripts/test-all.sh` (backend, frontend, extension, and the companion's unittest suite). `FOCUSQUEST_E2E=1 scripts/test-all.sh` also runs the Playwright end-to-end suite.
- **Restart the backend after pulling:** schema changes are Flyway migrations and run at startup. The latest is `V14`.
- **Commit only your own files.** Another Claude session has worked in this folder at the same time. Check `git status`, stage files by name, and do not sweep in someone else's unfinished work.
- **Commits:** the user reviews each step, then asks for the commit. Each step is one commit, with a message explaining the why, ending with the co-author line the session is given.
- **Try the companion:** backend (`cd backend && ./gradlew bootRun`) and web app (`cd frontend && npm run dev`) running; in Settings turn camera verification on and pair; then `cd vision && .venv/bin/focusquest-companion pair` and `run --camera --preview` (or `run --script ...`). Restart the backend after changing `application.yml`.
