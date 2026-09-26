# Camera verification: where the work stands

This note is for picking the work up again. It says what is built, what comes next, and where the details live. The rules themselves are in the requirements specification (section 21, camera verification; section 19, privacy). How they are built is in the technical architecture.

The original plan had two phases. Phase A closed out the MVP. Phase B built everything the camera needs before any computer vision is written. The aim of Phase B is that the vision work only has to replace a fake detector with a real one.

## Status

| Step | What | Commit |
| --- | --- | --- |
| A1 | Open decisions in requirements §22 settled: XP, gems, penalty, completion, categories and more | `96c670f` |
| A2 | Streak freezes: 10 gems each, at most 2, daily only, only for whole gaps, bridge without counting (requirements §13) | `96c670f` |
| A3 | Complete export, and restore from Settings; tests for retried requests and for restarts | `272d453` |
| A4 | Wider end-to-end coverage | **Open.** Only 4 Playwright tests; `dba2126` updated them for daily blocking |
| B1 | Specification and privacy rules for camera verification | `97afff4` |
| B2 | Consent in Settings, versioned (`CameraSettingsService.CONSENT_VERSION` and `utils/cameraConsent.ts` must match) | `2c99a93` |
| B3 | Per-category camera profiles: work area, warning timings, grace, minimum confidence | `eb6e2e7` |
| B4 and B5 | Observations become off-task episodes; off-task time is subtracted from the session and the streak; disputes | `e42f1c4` |
| B6 | Companion pairing: `fqc_` token, heartbeat and observation endpoints, connected status | `ea88436` |
| B7 | Warnings in the web app within 5 seconds (polling), with a countdown and "Not accurate?" | `0036c5e` |
| B8 | Camera in the session screens: "Verify with camera" switch, net focused time, history episodes | `285eed3` |
| **B9** | **The companion program itself, with a scripted fake detector** | **Next** |
| B10 | An end-to-end test that drives a session through a fake companion | With B9 |

Another session also changed website blocking in `3d6a796`: sites are now blocked every day until the daily target is reached, and throughout any running session.

## Next: the companion program (B9)

Build a new top-level `vision/` Python project, with a `pyproject.toml`. It runs on the user's computer next to the backend (`http://127.0.0.1:8080`).

- **Pairing.** The user clicks "Pair the companion program" in Settings, under Camera verification, and pastes the `fqc_…` code into the program. The program stores it locally and sends it as `Authorization: Bearer fqc_…`. The token works only on `/api/companion/**`.
- **Heartbeat.**
  - Call `POST /api/companion/heartbeat` every few seconds. The program counts as connected if it was heard from within the last 30 seconds.
  - The answer is `{ cameraOn, sessionId, profile, state, warnedAt, deductionStartsAt }`.
  - Open the camera only while `cameraOn` is true, and close it as soon as it is false (paused, ended, or camera verification turned off).
  - `profile` says which signals to look for (`AWAY`, `PHONE`, `LOOKING_AWAY`) and the work area (`SCREEN`, `SCREEN_OR_DESK`, `ANYWHERE`).
- **Observations.**
  - Send `POST /api/companion/observations` with `{ sessionId, observations: [{ clientEventId, signal, confidence, startedAt, observedUntil }] }`.
  - Each observation is one continuous stretch of one signal. Send the same `clientEventId` again with a later `observedUntil` to extend the stretch while it lasts; its signal and start cannot change.
  - At most 200 observations per request, none more than 5 seconds ahead of the backend's clock. The answer is the same as the heartbeat's, and the request also counts as a heartbeat.
- **Warning.** When the answer's `state` is `WARNED`, show an operating-system notification that counts down to `deductionStartsAt`. When it is `DEDUCTING`, say that the time is not counting.
- **Detector.** Define a `Detector` interface, and a `ScriptedDetector` that replays a timeline, for example "present for 60 s, then a phone for 90 s". It lets the whole pipeline, and B10's end-to-end test, run before any vision model exists. The real detector later goes behind the same interface.
- **Privacy.** Frames are processed in memory only: never saved to disk, never uploaded, never sent to the backend. There is no facial recognition. Requirements §19 and §21 are binding.

## After that: the real detector

Python with OpenCV and MediaPipe, plus PyTorch only if a custom model becomes necessary. It needs to detect three things:

- presence (someone in front of the camera, or away);
- head pose against the category's work area (looking away);
- a phone in the user's hands.

Then tune the timings and minimum confidence (`focusquest.camera.*` in `backend/src/main/resources/application.yml`), and measure accuracy against disputed episodes. That is the research question in requirements §21.

## Where the decisions live

- **Camera rules** (off-task signals, profiles, warning then subtraction, when time is settled, disputes, failure behaviour): requirements specification §21.
- **Privacy:** §19.
- **Freezes and streak loss:** §13.
- **Open questions and their answers:** §22. #5, on how long an abandoned session's websites stay blocked, is still marked provisional, and the daily-blocking change may have settled it; check.
- **Off-task time model, companion endpoints, token scoping and the web app pieces:** technical architecture. Search for "Off-task time model", "Pairing the companion program" and "Off-task warnings in the web app".

## Known limits

- Where off-task time crosses midnight or the start of the week, the day split can be off by up to a second.
- Observations that arrive after their stretch of time was settled change nothing already settled.
- Users cannot edit the camera profiles yet; this was decided (requirements §22, #17).

## Working in this repository

- **Run every test:** `scripts/test-all.sh`. `FOCUSQUEST_E2E=1 scripts/test-all.sh` also runs the Playwright end-to-end suite.
- **Restart the backend after pulling:** schema changes are Flyway migrations and run at startup. The latest is `V14`.
- **Commit only your own files.** Another Claude session has worked in this folder at the same time. Check `git status`, stage files by name, and do not sweep in someone else's unfinished work.
- **Commits:** the user reviews each step, then asks for the commit. Each step is one commit, with a message explaining the why.
