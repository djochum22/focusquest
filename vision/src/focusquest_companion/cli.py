"""focusquest-companion: pair once, then run next to the backend."""
from __future__ import annotations

import argparse
import getpass
import signal
import sys
import threading
from pathlib import Path

from . import config
from .api import DEFAULT_BACKEND_URL, BackendClient, BackendClock, BackendError, Unauthorized
from .companion import Companion
from .detector import ScriptedDetector, parse_script
from .notifications import SystemNotifier, TerminalNotifier, WarningPresenter

TOKEN_PREFIX = "fqc_"


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

    run = commands.add_parser("run", help="watch during camera-verified sessions")
    run.add_argument("--script", required=True,
                     help="replay a timeline instead of reading a camera, e.g. 'present 60s, phone 90s'. "
                          "Required until real detection exists.")
    run.add_argument("--loop", action="store_true", help="start the script over when it ends")
    run.add_argument("--no-notifications", action="store_true",
                     help="write warnings to the terminal instead of showing notifications")

    args = parser.parse_args(argv)
    path = args.config or config.default_path()
    try:
        if args.command == "pair":
            return _pair(path, args.code, args.backend)
        if args.command == "unpair":
            print("Forgot the pairing." if config.forget(path) else "Not paired.")
            return 0
        return _run(path, args.script, args.loop, args.no_notifications)
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


def _run(path: Path, script: str, loop: bool, no_notifications: bool) -> int:
    pairing = config.load(path)
    if pairing is None:
        print("Not paired yet. Run `focusquest-companion pair` with the code from Settings.", file=sys.stderr)
        return 2
    try:
        detector = ScriptedDetector(parse_script(script), loop=loop)
    except ValueError as error:
        print(error, file=sys.stderr)
        return 2

    log = _flushing_print
    notifier = TerminalNotifier(log) if no_notifications else SystemNotifier(log)
    companion = Companion(BackendClient(pairing.backend_url, pairing.token), detector,
                          WarningPresenter(notifier), BackendClock(), log=log)
    stop = threading.Event()
    for number in (signal.SIGINT, signal.SIGTERM):
        signal.signal(number, lambda *_: stop.set())
    log(f"Connected to {pairing.backend_url}, replaying a script instead of a camera. Stop with Ctrl+C.")
    try:
        companion.run(stop)
    except Unauthorized:
        print("The pairing code no longer works: it was unpaired, or paired again. "
              "Pair again in Settings, then run `focusquest-companion pair`.", file=sys.stderr)
        return 1
    log("Stopped.")
    return 0


def _flushing_print(line: str) -> None:
    # Flushed at once, so whoever reads the output (a terminal, a log, a test) sees each line as it happens.
    print(line, flush=True)
