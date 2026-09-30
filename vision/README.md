# Camera companion

The program that checks, during a camera-verified session, that you stay on task. It runs on your
computer next to the backend, is told by it when to watch and what for, and reports what it sees as
observations: which off-task signal (away, a phone in hand, looking away), how confident it is, and
when. The backend decides what counts as off task, when to warn, and what time to subtract
(requirements specification, section 21).

**With a camera, it checks presence only so far**: nobody in front of the camera is `AWAY`.
Looking away and a phone in hand come next. It can also replay a scripted timeline in place of a
camera, so the whole pipeline, warnings included, can be tried without one; both go behind the same
interface (`Detector` in `detector.py`).

**Privacy.** Frames are processed in memory only, each replacing the last: never saved to disk,
never uploaded, never sent to the backend. There is no facial recognition: the face detector finds
that a face is there, not whose it is. The camera is open only while the backend says a
camera-verified session is running, and is closed as soon as it does not, or cannot be reached.

## Run it

Python 3.11 or newer. The scripted detector needs nothing else; a camera needs OpenCV and MediaPipe,
from the `camera` extra. From this folder:

```bash
python3 -m venv .venv && .venv/bin/pip install -e '.[camera]'   # gives you .venv/bin/focusquest-companion
```

Without a camera you can skip installing: use `PYTHONPATH=src python3 -m focusquest_companion`
wherever the steps below say `focusquest-companion`.

1. In the web app, open Settings, turn on camera verification, and click **Pair the companion
   program**. Copy the code (it starts `fqc_`).
2. Pair, once:

   ```bash
   focusquest-companion pair           # asks for the code; or pass it, and --backend for another address
   ```

   The code is kept in `~/.config/focusquest/companion.json` (readable by you only), or where
   `--config` says. Pairing again in Settings makes a new code, and the old one stops working.
3. Run it with the camera:

   ```bash
   focusquest-companion run --camera --preview
   ```

   `--camera` uses the computer's built-in camera, even when an iPhone is connected as a Continuity
   Camera. `focusquest-companion cameras` lists them; `--camera iphone` (part of a name) or
   `--camera 1` (a number from that list) picks another.

   The first run downloads the face model (about 230 KB) to `~/.cache/focusquest/models`. The
   camera opens only once a session with **Verify with camera** is running; macOS asks the first
   time whether your terminal may use it. `--preview` opens a window showing what the camera sees
   and whether it finds your face; leave it out for normal use. Leave the camera for 90 seconds and
   you are warned; 60 seconds later the time stops counting.

   Or with a script in place of the camera, to see the warnings quickly:

   ```bash
   focusquest-companion run --script "present 60s, phone 90s"
   ```

   After 20 seconds of phone you are warned, and 60 seconds later the time stops counting. The web
   app shows the same warning.

A script is a list of steps, each `present`, `away`, `phone` or `looking-away` with a duration in
`s`, `m` or `h`, and optionally `@` a confidence (0.9 by default): `"away 3m @0.8, present 30s"`. It
starts when the camera opens (so again after each resume), and after its last step you are present,
unless you add `--loop`. `--no-notifications` writes the warnings to the terminal instead of
showing system notifications (macOS through `osascript`, Linux through `notify-send`).

`focusquest-companion unpair` forgets the code on this computer; **Unpair** in Settings makes it
stop working.

## How it works

| File | What it does |
| --- | --- |
| `companion.py` | The loop: looks once a second, reports every 2 seconds, opens and closes the detector as the backend says |
| `detector.py` | The `Detector` interface and the `ScriptedDetector` |
| `camera.py` | The `CameraDetector`: the webcam, MediaPipe's face detector, and the preview window |
| `cameras.py` | Picks the camera by name (the built-in one by default) and finds OpenCV's number for it |
| `models.py` | Downloads the models once and checks each against a pinned SHA-256 |
| `observations.py` | Turns moment-by-moment detections into stretches of one signal, each sent again with a later end while it lasts |
| `api.py` | The backend's companion endpoints, and its clock |
| `notifications.py` | System notifications for warnings |
| `cli.py`, `config.py` | The command, and where the pairing is kept |

- **Reports.** `POST /api/companion/observations` while there is something the backend has not
  heard yet, otherwise `POST /api/companion/heartbeat`. Either keeps the program connected (heard
  from within 30 seconds). Each answer says whether to watch (`cameraOn`), the session's profile, and
  the off-task state.
- **The backend's clock.** Observations are stamped in the backend's time, from `serverTime` in each
  answer, since the backend refuses one more than 5 seconds ahead of its clock. This also lets the
  end-to-end suite move the backend's clock forward.
- **Stretches.** A stretch ends at the first moment its signal is not seen; the backend treats a
  short gap as flicker. Its confidence is the lowest seen during it. Only the signals the category
  checks are reported.
- **Presence.** No face found at 0.5 or above is `AWAY`, with confidence 1 minus the best face's
  score, so a doubtful face is a doubtful absence. The webcam is read continuously on a background
  thread, keeping only the latest frame, so what is looked at is from now.
- **A camera that cannot be used** (no permission, unplugged, taken by another app) is tried again at
  each report. Meanwhile nothing is observed, so nothing is subtracted.
- **MediaPipe below 1.0.** 1.0.1 aborts on macOS as the face detector starts, so the extra pins
  `mediapipe<1`.
- **Refused reports** (the session ended or paused in the meantime) are dropped, and the program
  asks the backend what to do next.

## Tests

```bash
PYTHONPATH=src:tests python3 -m unittest discover -s tests
```

They need neither OpenCV nor MediaPipe: the camera code is tested with a fake camera and model.
`scripts/test-all.sh` runs them too. The end-to-end suite (`e2e/tests/camera.spec.ts`) runs the
program for real against the backend and the web app; set `FOCUSQUEST_PYTHON` if `python3` is not
the Python to use.
