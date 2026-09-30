# Camera companion

The program that checks, during a camera-verified session, that you stay on task. It runs on your
computer next to the backend, is told by it when to watch and what for, and reports what it sees as
observations: which off-task signal (away, a phone in hand, looking away), how confident it is, and
when. The backend decides what counts as off task, when to warn, and what time to subtract
(requirements specification, section 21).

**It cannot read a camera yet.** It replays a scripted timeline in place of one, so the whole
pipeline, warnings included, can be tried and tested before any vision model exists. The real
detector will go behind the same interface (`Detector` in `detector.py`).

**Privacy.** Frames are processed in memory only: never saved to disk, never uploaded, never sent to
the backend. There is no facial recognition. The camera is open only while the backend says a
camera-verified session is running, and is closed as soon as it does not, or cannot be reached.

## Run it

Python 3.11 or newer; nothing else to install for now. From this folder:

```bash
python3 -m venv .venv && .venv/bin/pip install -e .   # optional: gives you .venv/bin/focusquest-companion
```

Without installing, use `PYTHONPATH=src python3 -m focusquest_companion` wherever the steps below
say `focusquest-companion`.

1. In the web app, open Settings, turn on camera verification, and click **Pair the companion
   program**. Copy the code (it starts `fqc_`).
2. Pair, once:

   ```bash
   focusquest-companion pair           # asks for the code; or pass it, and --backend for another address
   ```

   The code is kept in `~/.config/focusquest/companion.json` (readable by you only), or where
   `--config` says. Pairing again in Settings makes a new code, and the old one stops working.
3. Run it, with a script in place of the camera:

   ```bash
   focusquest-companion run --script "present 60s, phone 90s"
   ```

   Then start a session with **Verify with camera** on. After 20 seconds of phone you are warned,
   and 60 seconds later the time stops counting. The web app shows the same warning.

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
- **Refused reports** (the session ended or paused in the meantime) are dropped, and the program
  asks the backend what to do next.

## Tests

```bash
PYTHONPATH=src:tests python3 -m unittest discover -s tests
```

`scripts/test-all.sh` runs them too. The end-to-end suite (`e2e/tests/camera.spec.ts`) runs the
program for real against the backend and the web app; set `FOCUSQUEST_PYTHON` if `python3` is not
the Python to use.
