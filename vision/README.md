# Camera companion

The program that checks, during a camera-verified session, that you stay on task. It runs on your
computer next to the backend, is told by it when to watch and what for, and reports what it sees as
observations: which off-task signal (away, a phone in hand, looking away), how confident it is, and
when. The backend decides what counts as off task, when to warn, and what time to subtract
(requirements specification, section 21).

**With a camera** nobody in front of it is `AWAY`, a head turned away from the category's work area
is `LOOKING_AWAY`, and a phone in a hand is `PHONE`. It can also replay a scripted timeline in place
of a camera, so the whole pipeline, warnings included, can be tried without one; both go behind the same
interface (`Detector` in `detector.py`).

**Privacy.** Frames are processed in memory only, each replacing the last: never saved to disk,
never uploaded, never sent to the backend. There is no facial recognition: the face detector finds
that a face is there, not whose it is; the other models find which way the head is turned, phones
and hands.
Calibrating keeps only angles, never a picture. The camera is open only while the backend says a
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
3. Calibrate, once, so it knows where your screen and desk are as this camera sees them. While the
   program runs, click **Calibrate** in Settings, under Camera verification; or, from a terminal:

   ```bash
   focusquest-companion calibrate
   ```

   A camera window opens in front of the others and guides you: look at your screen for 5 seconds,
   across all of it, then down at your desk as when reading or writing on paper, each after a
   3-second countdown. It keeps the range of head angles seen in
   `calibration.json` next to the pairing. Only angles are kept. Since mostly the eyes move when
   reading a screen, each range is made at least 20 degrees wide and 14 tall around its middle.
   Calibrate sitting as you usually work. Settings shows when it was last calibrated, or why it did
   not work. Calibrating from Settings is refused during a camera-verified session, which has the
   camera. Calibrate again after moving the
   camera or switching to another; `--camera` picks one as for `run`. Without a calibration, fixed
   angles around straight ahead are used, and `run` says so.
4. Run it with the camera:

   ```bash
   focusquest-companion run --camera --preview
   ```

   `--camera` uses the computer's built-in camera, even when an iPhone is connected as a Continuity
   Camera. `focusquest-companion cameras` lists them; `--camera iphone` (part of a name) or
   `--camera 1` (a number from that list) picks another.

   The first run downloads the models (about 19 MB) to `~/.cache/focusquest/models`. The
   camera opens only once a session with **Verify with camera** is running; macOS asks the first
   time whether your terminal may use it. `--preview` opens a window showing what the camera sees,
   whether it finds your face, the head's angles and the calibrated ones, and any phones and hands;
   leave it out for normal use. Leave the camera for 90 seconds, hold a phone in view for 5, or
   (in a category that checks it) look away for 15, and you are warned; 60 seconds later the time stops counting.

   Or with a script in place of the camera, to see the warnings quickly:

   ```bash
   focusquest-companion run --script "present 60s, phone 90s"
   ```

   After 5 seconds of phone you are warned, and 60 seconds later the time stops counting. The web
   app shows the same warning.

A script is a list of steps, each `present`, `away`, `phone`, `looking-away` or `unavailable` (the
camera cannot be used, as when another app has it) with a duration in
`s`, `m` or `h`, and optionally `@` a confidence (0.9 by default): `"away 3m @0.8, present 30s"`. It
starts when the camera opens (so again after each resume), and after its last step you are present,
unless you add `--loop`. `--no-notifications` writes the warnings to the terminal instead of
showing system notifications (macOS through `osascript`, Linux through `notify-send`).

`focusquest-companion unpair` forgets the code on this computer; **Unpair** in Settings makes it
stop working.

## In the background (macOS)

So that nobody has to start it from a terminal, the program can run in the background, started at
every login:

```bash
focusquest-companion install       # pairs first if needed (asks for the code), then starts it
focusquest-companion status        # paired? calibrated? running in the background?
focusquest-companion uninstall     # stops it, and it no longer starts at login
```

`install` downloads the models, then writes a launch agent, `~/Library/LaunchAgents/com.focusquest.companion.plist`,
which runs `run --camera --background` with the Python it was installed into, at login and again
30 seconds after it stops for any reason. What it does goes to `~/Library/Logs/FocusQuest/companion.log`,
each line with its time. It still opens the camera only during a session with **Verify with camera**,
and shows the same notifications. The first time it opens the camera, macOS asks whether it may.

With `--background`, an unpaired program waits to be paired, and a refused code waits for a new
pairing, instead of stopping: `focusquest-companion pair` in a terminal is enough, without restarting
it. Running `run --camera` in a terminal as well warns that the camera would be watched twice.

It runs in a macOS sandbox (`sandbox-exec`) that lets it connect only to this computer, where the
backend is. MediaPipe has Google's usage logging built in (which task ran, when it started, errors;
no pictures), sent to `play.googleapis.com`, with no switch to turn it off; the sandbox refuses it.
Its attempts show in the log as `Failed to send to clearcut`. The models are downloaded by `install`,
outside the sandbox. A backend on another computer cannot be reached from the sandbox, so the agent
then runs without it, and `install` says so. A `run` in a terminal is not sandboxed.

Moving or recreating `.venv` (or a Python upgrade that breaks it) breaks the agent; run `install`
again.

## How it works

| File | What it does |
| --- | --- |
| `companion.py` | The loop: looks once a second, reports every 2 seconds, opens and closes the detector as the backend says |
| `detector.py` | The `Detector` interface and the `ScriptedDetector` |
| `camera.py` | The `CameraDetector`: the webcam, MediaPipe's models (face detector, face landmarker, object detector, hand landmarker), and the preview window |
| `phones.py` | The phone-in-hand rule |
| `calibration.py` | Head angles, the screen and desk zones, the looking-away rule, and the saved calibration |
| `cameras.py` | Picks the camera by name (the built-in one by default) and finds OpenCV's number for it |
| `models.py` | Downloads the models once and checks each against a pinned SHA-256 |
| `observations.py` | Turns moment-by-moment detections into stretches of one signal, each sent again with a later end while it lasts |
| `api.py` | The backend's companion endpoints, and its clock |
| `notifications.py` | System notifications for warnings |
| `cli.py`, `config.py` | The command, and where the pairing is kept |
| `service.py` | The macOS launch agent that runs it in the background |

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
- **Looking away**, only where the category checks it. MediaPipe's face landmarker gives the head's
  yaw (left and right) and pitch (up and down) from its facial transformation matrix. More than 5
  degrees beyond the screen's angles (`SCREEN`), or beyond both the screen's and the desk's
  (`SCREEN_OR_DESK`), is `LOOKING_AWAY`. For a clearly seen face (0.7 or above) its confidence is the
  backend's minimum of 0.7 just past that margin, so it counts at once, and grows to 1 over the next
  15 degrees; a less sure face lowers it in proportion. When a look away starts, the head's angles and the work area's are written to the
  output, so the log shows why. A face lost within 2 seconds of being seen turned away stays `LOOKING_AWAY` until it
  is found again, since a head turned far enough loses the face; otherwise a lost face is `AWAY`.
- **A phone in hand.** MediaPipe's object detector (EfficientDet-Lite0, trained on COCO) looks for
  its "cell phone" class; once it finds one at 0.25 or above, the hand landmarker looks for hands.
  A phone whose box overlaps a hand's is `PHONE`; a phone lying on the desk is not. Both models look
  at the whole frame and again at two enlarged crops of its lower part (left and right, each 60 % of
  the width and height), since the detector shrinks what it looks at to 320 by 320 pixels, and a
  phone held further from the camera, or half out of the picture, is otherwise too small to score. Its scores are
  low even for a phone held up in plain view (0.3 to 0.8 on sample photos), so a phone in a hand
  is reported from the backend's minimum, 0.7 at a score of 0.25, up to 1 at 0.6. The camera sees
  only what is in front of it: a phone held low in the lap, below a laptop camera's view, is not
  seen.
- **A camera that cannot be used** (no permission, unplugged, taken by another app) is tried again at
  each report. Meanwhile nothing is observed, so nothing is subtracted, and each report says why
  (`cameraProblem`), so the web app can say the session is not being checked.
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
