"""focusquest-companion: pair once, then run next to the backend."""
from __future__ import annotations

import argparse
import getpass
import os
import signal
import sys
import threading
from datetime import datetime
from pathlib import Path

from . import accuracy, calibration, config, labelling, models, service
from .api import DEFAULT_BACKEND_URL, BackendClient, BackendClock, BackendError, Unauthorized
from .cameras import CameraChoice, list_cameras
from .companion import Companion
from .detector import DetectorUnavailable, ScriptedDetector, parse_script
from .notifications import SystemNotifier, TerminalNotifier, WarningPresenter

TOKEN_PREFIX = "fqc_"
#: How long to look at the screen, then at the desk, while calibrating.
CALIBRATION_SECONDS = 5
#: In the background, how often to check whether the program has been paired (again).
PAIRING_CHECK_SECONDS = 5


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="focusquest-companion",
        description="FocusQuest's camera companion. Frames are analysed in memory on this computer only: "
                    "never saved, uploaded or sent to FocusQuest.")
    parser.add_argument("--config", type=Path, default=None,
                        help=f"where the pairing is kept (default: {config.default_path()})")
    commands = parser.add_subparsers(dest="command", required=True)

    pair = commands.add_parser("pair", help="pair with the code from Settings, under Camera verification")
    pair.add_argument("code", nargs="?", help="the fqc_ pairing code; asked for if left out")
    pair.add_argument("--backend", default=DEFAULT_BACKEND_URL, help=f"the backend's address (default: {DEFAULT_BACKEND_URL})")

    commands.add_parser("unpair", help="forget the pairing on this computer")

    commands.add_parser("cameras", help="list the cameras, and which one --camera uses")

    calibrate = commands.add_parser(
        "calibrate", help="show the program where your screen and desk are, so it can tell when you look away")
    calibrate.add_argument("--camera", default="built-in", metavar="CAMERA",
                           help="the camera to calibrate: the built-in one by default, or as for `run --camera`")
    # The window is always shown now, since it guides; the flag is still accepted.
    calibrate.add_argument("--preview", action="store_true", help=argparse.SUPPRESS)

    run = commands.add_parser("run", help="watch during camera-verified sessions")
    source = run.add_mutually_exclusive_group(required=True)
    source.add_argument("--camera", nargs="?", const="built-in", metavar="CAMERA",
                        help="watch with a camera: the computer's built-in one, or CAMERA, part of a name "
                             "(see `cameras`) or a number. Checks presence, where you look and a phone in hand.")
    source.add_argument("--script", help="replay a timeline instead of reading a camera, e.g. 'present 60s, phone 90s'")
    run.add_argument("--preview", action="store_true",
                     help="with --camera: show what the camera sees and what it found, in a window")
    run.add_argument("--loop", action="store_true", help="with --script: start the script over when it ends")
    run.add_argument("--no-notifications", action="store_true",
                     help="write warnings to the terminal instead of showing notifications")
    run.add_argument("--background", action="store_true",
                     help="as the launch agent runs it: wait to be paired rather than stop, and put the time on each line")

    install = commands.add_parser(
        "install", help="on macOS: pair if needed, then start watching in the background at every login")
    install.add_argument("code", nargs="?", help="the fqc_ pairing code, if not paired yet; asked for if left out")
    install.add_argument("--backend", default=DEFAULT_BACKEND_URL,
                         help=f"the backend's address, if not paired yet (default: {DEFAULT_BACKEND_URL})")

    label = commands.add_parser(
        "label", help="say what you are doing while the camera looks, to measure how often it is right "
                      "(numbers only, written to a CSV file on this computer)")
    label.add_argument("--category", required=True, type=str.upper, choices=sorted(labelling.WORK_AREAS),
                       help="the session category to check for, as in a session")
    label.add_argument("--out", type=Path, default=None,
                       help="the CSV file to write (default: ~/Documents/FocusQuest/labels-<date>-<time>.csv)")
    label.add_argument("--camera", default="built-in", metavar="CAMERA", help="as for `run --camera`")

    measure = commands.add_parser(
        "accuracy", help="how often the camera was right, from the files `label` wrote; thresholds can be tried "
                         "on them without labelling again")
    measure.add_argument("files", nargs="*", type=Path, metavar="FILE",
                         help=f"files written by `label` (default: every one in {labelling.default_path(datetime.now()).parent})")
    defaults = accuracy.Thresholds()
    measure.add_argument("--present-from", type=float, default=defaults.present_from, metavar="SCORE",
                         help=f"a face found at least this surely is there (today {defaults.present_from:g})")
    measure.add_argument("--margin", type=float, default=defaults.margin, metavar="DEGREES",
                         help=f"how far beyond the work area the head may turn and still be on task (today {defaults.margin:g})")
    measure.add_argument("--phone-from", type=float, default=defaults.phone_from, metavar="SCORE",
                         help=f"a phone in a hand found at least this surely counts (today {defaults.phone_from:g})")
    measure.add_argument("--certain-from", type=float, default=defaults.certain_from, metavar="SCORE",
                         help=f"from this score a phone in a hand is certain (today {defaults.certain_from:g})")
    measure.add_argument("--min-confidence", type=float, default=defaults.min_confidence, metavar="CONFIDENCE",
                         help=f"the backend's minimum: anything less sure is ignored (today {defaults.min_confidence:g})")
    measure.add_argument("--lost-for", type=float, default=defaults.lost_for, metavar="SECONDS",
                         help=f"how long a face lost after a turn away stays looking away (today {defaults.lost_for:g})")

    commands.add_parser("uninstall", help="stop watching in the background, and do not start at login")
    commands.add_parser("status", help="whether the program runs in the background, and where its log is")

    argv = sys.argv[1:] if argv is None else argv
    args = parser.parse_args(argv)
    path = args.config or config.default_path()
    try:
        if args.command == "pair":
            return _pair(path, args.code, args.backend)
        if args.command == "unpair":
            print("Forgot the pairing." if config.forget(path) else "Not paired.")
            return 0
        if args.command == "cameras":
            return _cameras()
        if args.command == "calibrate":
            return _calibrate(calibration.default_path(path), args.camera)
        if args.command == "label":
            return _label(path, args, argv)
        if args.command == "accuracy":
            return _accuracy(path, args)
        if args.command == "install":
            return _install(path, args.code, args.backend)
        if args.command == "uninstall":
            return _uninstall()
        if args.command == "status":
            return _status(path)
        if args.preview and args.script is not None:
            parser.error("--preview only works with --camera")
        return _run(path, args, args.no_notifications)
    except BackendError as error:
        print(error, file=sys.stderr)
        return 1


def _pair(path: Path, code: str | None, backend_url: str) -> int:
    code = (code or getpass.getpass("Pairing code: ")).strip()
    if not code.startswith(TOKEN_PREFIX):
        print(f"A pairing code starts with {TOKEN_PREFIX}. Copy it from Settings, under Camera verification.",
              file=sys.stderr)
        return 2
    try:
        BackendClient(backend_url, code).heartbeat()
    except Unauthorized:
        print("The backend did not accept that code. Pair again in Settings and use the new code.", file=sys.stderr)
        return 1
    config.save(path, config.Pairing(backend_url, code))
    print(f"Paired with {backend_url}.")
    return 0


def _run(path: Path, args: argparse.Namespace, no_notifications: bool) -> int:
    log = _timed_print if args.background else _flushing_print
    stop = threading.Event()
    for number in (signal.SIGINT, signal.SIGTERM):
        signal.signal(number, lambda *_: stop.set())
    pairing = _await_pairing(path, None, args.background, stop, log)
    if pairing is None:
        return 0 if stop.is_set() else 2
    calibrator = None
    if args.script is not None:
        try:
            detector = ScriptedDetector(parse_script(args.script), loop=args.loop)
        except ValueError as error:
            print(error, file=sys.stderr)
            return 2
        source = "replaying a script instead of a camera"
    else:
        angles = calibration.load(calibration.default_path(path))
        from .camera import Preview

        detector = _camera_detector(args.camera, Preview() if args.preview else None, angles, log)
        if detector is None:
            return 2
        calibrator = _camera_calibrator(calibration.default_path(path), args.camera, log, detector.use_calibration)
        source = f"{CameraChoice.parse(args.camera).describe()}, checking presence, where you look and a phone in hand"
        if not angles.calibrated:
            source += " (not calibrated yet: calibrate from Settings, under Camera verification)"
        if not args.background and service.supported() and service.LaunchAgent().status().running:
            log("The program also runs in the background (see `focusquest-companion status`), so the camera is "
                "watched twice. Stop it with `focusquest-companion uninstall`, or stop this one.")

    notifier = TerminalNotifier(log) if no_notifications else SystemNotifier(log)
    while True:
        companion = Companion(BackendClient(pairing.backend_url, pairing.token), detector,
                              WarningPresenter(notifier), BackendClock(), calibrator=calibrator, log=log)
        log(f"Connected to {pairing.backend_url}, {source}." + ("" if args.background else " Stop with Ctrl+C."))
        try:
            companion.run(stop)
        except Unauthorized:
            if not args.background:
                print("The pairing code no longer works: it was unpaired, or paired again. "
                      "Pair again in Settings, then run `focusquest-companion pair`.", file=sys.stderr)
                return 1
            log("The pairing code no longer works: it was unpaired, or paired again in Settings.")
            pairing = _await_pairing(path, pairing, True, stop, log)
            if pairing is None:
                break
            continue
        break
    log("Stopped.")
    return 0


def _await_pairing(path: Path, refused: config.Pairing | None, wait: bool, stop: threading.Event,
                   log) -> config.Pairing | None:
    """The pairing, other than one the backend refused. In the background it waits for one; None once stopped."""
    said = False
    while True:
        pairing = config.load(path)
        if pairing is not None and pairing != refused:
            return pairing
        if not wait:
            print("Not paired yet. Run `focusquest-companion pair` with the code from Settings.", file=sys.stderr)
            return None
        if not said:
            log("Waiting to be paired: run `focusquest-companion pair` with the code from Settings, under "
                "Camera verification.")
            said = True
        if stop.wait(PAIRING_CHECK_SECONDS):
            return None


def _install(path: Path, code: str | None, backend_url: str) -> int:
    if not service.supported():
        print("Running in the background is set up on macOS only. Elsewhere, start "
              "`focusquest-companion run --camera` yourself, or from your system's autostart.", file=sys.stderr)
        return 2
    if not _camera_installed():
        return 2
    if config.load(path) is None:
        result = _pair(path, code, backend_url)
        if result != 0:
            return result
    try:
        for model in (models.FACE_DETECTOR, models.FACE_LANDMARKER, models.OBJECT_DETECTOR, models.HAND_LANDMARKER):
            models.ensure(model, log=_flushing_print)
    except models.ModelError as error:
        print(error, file=sys.stderr)
        return 1
    log = service.log_path()
    only_this_computer = service.is_this_computer(config.load(path).backend_url)
    try:
        service.LaunchAgent().install(service.agent(path.resolve(), log, only_this_computer=only_this_computer))
    except OSError as error:
        print(error, file=sys.stderr)
        return 1
    print("The companion now runs in the background, and starts again at every login. The camera opens only "
          "during a session with Verify with camera; the first time, macOS asks whether it may use it.")
    print(f"What it does is written to {log}. Stop it with `focusquest-companion uninstall`.")
    if only_this_computer:
        print("It can connect only to this computer, where the backend is: nothing it does can leave it.")
    else:
        print("The backend is on another computer, so the companion's connections are not limited to this one.")
    if not calibration.load(calibration.default_path(path)).calibrated:
        print("Not calibrated yet: calibrate from Settings, under Camera verification, so it knows where your "
              "screen and desk are.")
    return 0


def _uninstall() -> int:
    if not service.supported():
        print("Nothing to remove: running in the background is set up on macOS only.")
        return 0
    removed = service.LaunchAgent().uninstall()
    print("Stopped, and it no longer starts at login. The pairing is kept; `unpair` forgets it."
          if removed else "Not running in the background.")
    return 0


def _status(path: Path) -> int:
    paired = config.load(path)
    print(f"Paired with {paired.backend_url}." if paired else "Not paired.")
    print("Calibrated." if calibration.load(calibration.default_path(path)).calibrated else "Not calibrated.")
    if not service.supported():
        print("Running in the background is set up on macOS only.")
        return 0
    status = service.LaunchAgent().status()
    if status.running:
        print(f"Running in the background (process {status.pid}). Log: {service.log_path()}")
    elif status.installed:
        print(f"Installed, but not running right now; launchd starts it again shortly. Log: {service.log_path()}")
    else:
        print("Not running in the background. `focusquest-companion install` sets that up.")
    return 0


def _cameras() -> int:
    cameras = list_cameras()
    if cameras is None:
        print("Cameras can only be listed on macOS. `--camera` uses camera 0; `--camera 1` the next.")
        return 0
    if not cameras:
        print("No cameras found.")
        return 1
    try:
        default = CameraChoice().resolve(cameras).index
    except DetectorUnavailable:
        default = None
    for camera in cameras:
        print(f"{camera.index}  {camera.name}{'   (used by --camera)' if camera.index == default else ''}")
    return 0


def _calibrate(path: Path, wanted: str) -> int:
    if not _camera_installed():
        return 2
    from .calibration import CalibrationFailed

    try:
        calibrator = _camera_calibrator(path, wanted, _flushing_print)
    except models.ModelError as error:
        print(error, file=sys.stderr)
        return 1
    print("Calibrating where you look while you work: follow the steps in the camera window. "
          "Only the head's angles are kept, never a picture.")
    try:
        calibrator.calibrate()
    except (CalibrationFailed, DetectorUnavailable) as error:
        print(error, file=sys.stderr)
        return 1
    print(f"Saved to {path}. Calibrate again after moving the camera or switching to another one.")
    return 0


def _label(path: Path, args: argparse.Namespace, argv: list[str]) -> int:
    if not _camera_installed():
        return 2
    if service.supported() and os.environ.get(service.SANDBOXED) != "1":
        return _label_in_sandbox(argv)
    out = args.out or labelling.default_path(datetime.now())
    angles = calibration.load(calibration.default_path(path))
    from .camera import Preview

    window = Preview()
    detector = _camera_detector(args.camera, window, angles, _flushing_print)
    if detector is None:
        return 2
    try:
        out.parent.mkdir(parents=True, exist_ok=True)
        file = out.open("x", newline="")
    except FileExistsError:
        print(f"{out} already exists. Give another file with --out.", file=sys.stderr)
        return 2
    profile = labelling.profile_for(args.category)
    print(f"Labelling a {args.category} session: press keys in the camera window, {labelling.KEY_HELP}. "
          "Press the key as soon as you change what you do.")
    print(f"Writing one row of numbers a second to {out}; never a picture, and nothing leaves this computer.")
    if not angles.calibrated:
        print("Not calibrated yet: looking away is judged against fixed angles. Calibrate from Settings first.")
    if service.supported() and service.LaunchAgent().status().running:
        print("The companion also runs in the background. That is fine, but do not run a session with "
              "Verify with camera while labelling.")

    def show_label(label: str) -> None:
        window.notes = [f"Labelled: {label.lower().replace('_', ' ')}", labelling.KEY_HELP]

    labeller = labelling.Labeller(detector, profile, file, window.next_key, show_label)
    try:
        with file:
            labeller.run()
    except KeyboardInterrupt:
        pass
    except DetectorUnavailable as error:
        print(error, file=sys.stderr)
        print(f"Wrote {labeller.rows} seconds to {out} before the camera stopped.", file=sys.stderr)
        return 1
    print(f"Wrote {labeller.rows} seconds to {out}.")
    return 0


def _accuracy(path: Path, args: argparse.Namespace) -> int:
    thresholds = accuracy.Thresholds(args.present_from, args.margin, args.phone_from, args.certain_from,
                                     args.min_confidence, args.lost_for)
    problem = thresholds.problem()
    if problem is not None:
        print(problem, file=sys.stderr)
        return 2
    folder = labelling.default_path(datetime.now()).parent
    files = args.files or accuracy.default_paths(folder)
    if not files:
        print(f"No files from `label` in {folder}. Label first, or give the files.", file=sys.stderr)
        return 2
    angles = calibration.load(calibration.default_path(path))
    try:
        report = accuracy.measure(files, angles, thresholds)
    except (OSError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1
    for line in accuracy.format_report(report):
        print(line)
    return 0


def _label_in_sandbox(argv: list[str]) -> int:
    """Starts ``label`` again in the sandbox that lets it connect only to this computer, as the launch
    agent runs, so MediaPipe's usage logging cannot reach Google. The models are downloaded first,
    outside it. Returns only if it cannot start."""
    try:
        for model in (models.FACE_DETECTOR, models.FACE_LANDMARKER, models.OBJECT_DETECTOR, models.HAND_LANDMARKER):
            models.ensure(model, log=_flushing_print)
    except models.ModelError as error:
        print(error, file=sys.stderr)
        return 1
    command = service.sandboxed([sys.executable, "-m", "focusquest_companion", *argv])
    os.execve(command[0], command, {**os.environ, service.SANDBOXED: "1"})
    return 1   # not reached: execve replaces this program, or raises


def _camera_calibrator(path: Path, wanted: str, log, on_calibrated=lambda angles: None):
    """Calibrates with its own view of the camera: the detector's is closed whenever calibrating is allowed."""
    from .camera import CameraCalibrator, MediaPipePoses, Webcam

    pose_model = models.ensure(models.FACE_LANDMARKER, log=log)
    choice = CameraChoice.parse(wanted)
    return CameraCalibrator(Webcam(lambda: choice.resolve(list_cameras()), log), lambda: MediaPipePoses(pose_model),
                            path, CALIBRATION_SECONDS, on_calibrated=on_calibrated, say=log)


def _camera_installed() -> bool:
    try:
        import cv2  # noqa: F401  (checked here, used once the camera opens)
        import mediapipe  # noqa: F401
    except ImportError:
        print("The camera needs OpenCV and MediaPipe. From vision/, run:\n"
              "  python3 -m venv .venv && .venv/bin/pip install -e '.[camera]'\n"
              "and start the program with .venv/bin/focusquest-companion.", file=sys.stderr)
        return False
    return True


def _camera_detector(wanted: str, preview, angles: calibration.Calibration, log):
    """``preview``: the window (a :class:`~.camera.Preview`) to show what it sees in, or None."""
    if not _camera_installed():
        return None
    from .camera import CameraDetector, MediaPipeFaces, MediaPipeHands, MediaPipePhones, MediaPipePoses, Webcam

    try:
        face_model, pose_model, phone_model, hand_model = (models.ensure(model, log=log) for model in (
            models.FACE_DETECTOR, models.FACE_LANDMARKER, models.OBJECT_DETECTOR, models.HAND_LANDMARKER))
    except models.ModelError as error:
        print(error, file=sys.stderr)
        return None
    choice = CameraChoice.parse(wanted)
    webcam = Webcam(lambda: choice.resolve(list_cameras()), log)
    return CameraDetector(webcam, lambda: MediaPipeFaces(face_model), poses=lambda: MediaPipePoses(pose_model),
                          phones=lambda: MediaPipePhones(phone_model), hands=lambda: MediaPipeHands(hand_model),
                          calibration=angles, preview=preview, log=log)


def _flushing_print(line: str) -> None:
    # Flushed at once, so whoever reads the output (a terminal, a log, a test) sees each line as it happens.
    print(line, flush=True)


def _timed_print(line: str) -> None:
    # In the log file there is nobody watching as it happens, so each line says when.
    print(f"{datetime.now():%Y-%m-%d %H:%M:%S}  {line}", flush=True)
