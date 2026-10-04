"""Running in the background on macOS: a launch agent starts the companion at login, and again if it stops.

The agent runs ``run --camera`` with the Python this program was installed into, so OpenCV and
MediaPipe are found. Its output goes to a log file, since there is no terminal to show it. The
camera still opens only while the backend says a camera-verified session is running.
"""
from __future__ import annotations

import os
import plistlib
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Sequence

LABEL = "com.focusquest.companion"
#: launchd waits this long before starting the program again after it stops, so a crash does not spin.
RESTART_AFTER_SECONDS = 30

Runner = Callable[[Sequence[str]], subprocess.CompletedProcess]


def _run(command: Sequence[str]) -> subprocess.CompletedProcess:
    return subprocess.run(list(command), capture_output=True, text=True)


def agent_path() -> Path:
    return Path.home() / "Library" / "LaunchAgents" / f"{LABEL}.plist"


def log_path() -> Path:
    return Path.home() / "Library" / "Logs" / "FocusQuest" / "companion.log"


def supported() -> bool:
    return sys.platform == "darwin"


def agent(config_path: Path, log: Path, python: str = sys.executable) -> dict:
    """The launch agent's definition: what to run, and when."""
    return {
        "Label": LABEL,
        "ProgramArguments": [python, "-m", "focusquest_companion", "--config", str(config_path),
                             "run", "--camera", "--background"],
        "RunAtLoad": True,
        "KeepAlive": True,
        "ThrottleInterval": RESTART_AFTER_SECONDS,
        # Only in a logged-in desktop session: it needs the camera, notifications and windows.
        "LimitLoadToSessionType": "Aqua",
        "ProcessType": "Interactive",
        "StandardOutPath": str(log),
        "StandardErrorPath": str(log),
        "EnvironmentVariables": {"PYTHONUNBUFFERED": "1"},
    }


@dataclass(frozen=True)
class Status:
    installed: bool
    running: bool
    pid: int | None = None


class LaunchAgent:
    def __init__(self, path: Path | None = None, run: Runner = _run, uid: int | None = None):
        self.path = path or agent_path()
        self._run = run
        self._domain = f"gui/{os.getuid() if uid is None else uid}"

    def install(self, definition: dict) -> None:
        """Writes the agent and starts it, replacing one installed before."""
        self.path.parent.mkdir(parents=True, exist_ok=True)
        Path(definition["StandardOutPath"]).parent.mkdir(parents=True, exist_ok=True)
        self._run(["launchctl", "bootout", f"{self._domain}/{LABEL}"])   # fails harmlessly when not loaded
        self.path.write_bytes(plistlib.dumps(definition))
        result = self._run(["launchctl", "bootstrap", self._domain, str(self.path)])
        if result.returncode != 0:
            raise OSError(f"launchctl could not start the companion: {(result.stderr or result.stdout).strip()}")

    def uninstall(self) -> bool:
        """Stops the agent and removes it. False if it was not installed."""
        self._run(["launchctl", "bootout", f"{self._domain}/{LABEL}"])
        try:
            self.path.unlink()
            return True
        except FileNotFoundError:
            return False

    def status(self) -> Status:
        result = self._run(["launchctl", "print", f"{self._domain}/{LABEL}"])
        if result.returncode != 0:
            return Status(installed=self.path.exists(), running=False)
        pid = None
        for line in result.stdout.splitlines():
            key, _, value = line.strip().partition(" = ")
            if key == "pid" and value.isdigit():
                pid = int(value)
        return Status(installed=True, running=pid is not None, pid=pid)
