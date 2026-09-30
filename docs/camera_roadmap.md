# Camera verification: where the work stands

This note is for picking the work up again. It says what is built, what comes next, and where the details live. The rules themselves are in the requirements specification (section 21, camera verification; section 19, privacy). How they are built is in the technical architecture.

The original plan had two phases. Phase A closed out the MVP. Phase B built everything the camera needs before any computer vision is written. The aim of Phase B is that the vision work only has to replace a fake detector with a real one.

## Status

| Step | What | Commit |
| --- | --- | --- |
| A1 | Open decisions in requirements §22 settled: XP, gems, penalty, completion, categories and more | `96c670f` |
| A2 | Streak freezes: 10 gems each, at most 2, daily only, only for whole gaps, bridge without counting (requirements §13) | `96c670f` |
| A3 | Complete export, and restore from Settings; tests for retried requests and for restarts | `272d453` |
| A4 | Wider end-to-end coverage | **Open.** 6 Playwright tests: 4 for sessions and blocking (`dba2126` updated them for daily blocking), 2 for the camera (B10) |
| B1 | Specification and privacy rules for camera verification | `97afff4` |
| B2 | Consent in Settings, versioned (`CameraSettingsService.CONSENT_VERSION` and `utils/cameraConsent.ts` must match) | `2c99a93` |
| B3 | Per-category camera profiles: work area, warning timings, grace, minimum confidence | `eb6e2e7` |
| B4 and B5 | Observations become off-task episodes; off-task time is subtracted from the session and the streak; disputes | `e42f1c4` |
| B6 | Companion pairing: `fqc_` token, heartbeat and observation endpoints, connected status | `ea88436` |
| B7 | Warnings in the web app within 5 seconds (polling), with a countdown and "Not accurate?" | `0036c5e` |
| B8 | Camera in the session screens: "Verify with camera" switch, net focused time, history episodes | `285eed3` |
| B9 | The companion program (`vision/`), with a scripted fake detector; `serverTime` in the companion's answers | `03e4877` |
| B10 | End-to-end tests that drive a session through the real companion program | `03e4877` |
| C1a | Real camera, presence only: webcam capture, MediaPipe face detection, `--preview` | Not committed yet |
| **C1b** | **Looking away: head pose, with calibration** | **Next** |
| C1c | Phone in hand | |
| C1d | Smoothing, label mode, accuracy measurements, tuning | |

Another session also changed website blocking in `3d6a796`: sites are now blocked every day until the daily target is reached, and throughout any running session.

## The companion program (B9 and B10)

`vision/` is a Python 3.11 project with no dependencies yet; `vision/README.md` says how to pair and run it and how it works.

- It reports every 2 seconds (observations if there are any the backend has not heard, otherwise a heartbeat) and looks once a second.
- It opens the detector only while the answer says `cameraOn`, and closes it when it does not, or when the backend cannot be reached.
- Observations are stamped in the backend's clock, from the new `serverTime` in each answer. That keeps them within the backend's 5-second tolerance whatever this computer's clock says, and lets the end-to-end suite move the backend's clock.
- A stretch ends at the first moment its signal is not seen, and its confidence is the lowest seen during it. Only the category's signals are reported.
- Warnings become system notifications (macOS `osascript`, Linux `notify-send`): one when warned, a reminder 10 seconds before the time stops counting, and one when it stops and starts counting again.
- `ScriptedDetector` replays a timeline such as `"present 60s, phone 90s"`, from each time the detector opens. `run` requires `--script` until the real detector exists, and the Settings notice says so.
- `e2e/tests/camera.spec.ts` pairs it through Settings and runs it: warning, subtraction, dispute, camera closed on pause, and the session going on unverified when it stops.

## The real detector (C1)

Python with OpenCV and MediaPipe, plus PyTorch only if a custom model becomes necessary. It is `CameraDetector` in `vision/src/focusquest_companion/camera.py`, behind the same `Detector` interface as the script, chosen with `run --camera`.

**Built (C1a).** A webcam read on a background thread that keeps only the latest frame; MediaPipe's BlazeFace (short range), downloaded once to `~/.cache/focusquest/models` and checked against a pinned SHA-256; no face at 0.5 or above is `AWAY`, with confidence 1 minus the best face's score; `--preview` shows the frame and what was found. `--camera` picks the Mac's built-in camera by name even when an iPhone is connected as a Continuity Camera: OpenCV numbers cameras by unique ID, and `system_profiler` gives each camera's name and unique ID, so the number is looked up at each opening. A camera that cannot open or stops sending frames is retried at each report.

- **MediaPipe version:** 1.0.1 aborts on macOS when the face detector starts (in its Metal helper, even with the CPU delegate), so the `camera` extra pins `mediapipe<1`. 0.10.35 works.
- **Checked** on MediaPipe's sample portrait (present, face 0.92) and a blank frame (away), not yet on a live webcam.

**Next (C1b onwards).** It needs to detect three things:

- presence (someone in front of the camera, or away);
- head pose against the category's work area (looking away);
- a phone in the user's hands.

Then tune the timings and minimum confidence (`focusquest.camera.*` in `backend/src/main/resources/application.yml`), and measure accuracy against disputed episodes. That is the research question in requirements §21.

## Where the decisions live

- **Camera rules** (off-task signals, profiles, warning then subtraction, when time is settled, disputes, failure behaviour): requirements specification §21.
- **Privacy:** §19.
- **Freezes and streak loss:** §13.
- **Open questions and their answers:** §22. #5, on how long an abandoned session's websites stay blocked, was settled by the daily-blocking change.
- **Off-task time model, companion endpoints, token scoping and the web app pieces:** technical architecture. Search for "Off-task time model", "Pairing the companion program" and "Off-task warnings in the web app".

## Known limits

- Where off-task time crosses midnight or the start of the week, the day split can be off by up to a second.
- Observations that arrive after their stretch of time was settled change nothing already settled.
- Users cannot edit the camera profiles yet; this was decided (requirements §22, #17).
- When the camera closes (pause, end), observations from its last 2 seconds that were not reported yet are dropped.
- A head turned far to the side can lose the face entirely, which counts as away rather than looking away. The away warning takes 90 seconds, so a short turn does not matter; C1b's head pose should tell the two apart.
- While the camera cannot be used, the companion still reports, so the web app shows the session as checked. Nothing is observed, so nothing is subtracted, but the spec says the web app should say it is not being verified. This needs a camera-unavailable flag in the heartbeat and an off-task state for it.
- Operating-system notifications cannot count down, so the companion sends a reminder 10 seconds before the time stops counting instead.

## Working in this repository

- **Run every test:** `scripts/test-all.sh` (now including the companion's unittest suite). `FOCUSQUEST_E2E=1 scripts/test-all.sh` also runs the Playwright end-to-end suite.
- **Restart the backend after pulling:** schema changes are Flyway migrations and run at startup. The latest is `V14`.
- **Commit only your own files.** Another Claude session has worked in this folder at the same time. Check `git status`, stage files by name, and do not sweep in someone else's unfinished work.
- **Commits:** the user reviews each step, then asks for the commit. Each step is one commit, with a message explaining the why.
