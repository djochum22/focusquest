"""Operating-system notifications for off-task warnings."""
from __future__ import annotations

import math
import platform
import shutil
import subprocess
from datetime import datetime
from typing import Callable, Protocol

from .api import CompanionState

#: A second notification when this little of the grace period is left, since one cannot count down.
FINAL_REMINDER_SECONDS = 10


class Notifier(Protocol):
    def notify(self, title: str, message: str) -> None: ...


class SystemNotifier:
    """macOS through osascript, Linux through notify-send; elsewhere, or if that fails, the terminal."""

    def __init__(self, fallback: Callable[[str], None] = print):
        self._fallback = fallback

    def notify(self, title: str, message: str) -> None:
        command = self._command(title, message)
        if command is not None:
            try:
                subprocess.run(command, check=True, capture_output=True, timeout=5)
                return
            except (OSError, subprocess.SubprocessError):
                pass
        self._fallback(f"{title} {message}")

    @staticmethod
    def _command(title: str, message: str) -> list[str] | None:
        if platform.system() == "Darwin":
            # Passed as arguments, never spliced into the script, so no text can break out of it.
            script = "on run argv\ndisplay notification (item 2 of argv) with title (item 1 of argv)\nend run"
            return ["osascript", "-e", script, "FocusQuest: " + title, message]
        if shutil.which("notify-send"):
            return ["notify-send", "--app-name=FocusQuest", title, message]
        return None


class TerminalNotifier:
    def __init__(self, write: Callable[[str], None] = print):
        self._write = write

    def notify(self, title: str, message: str) -> None:
        self._write(f"{title} {message}")


def seconds_left(state: CompanionState, now: datetime) -> int:
    if state.deduction_starts_at is None:
        return 0
    return max(0, math.ceil((state.deduction_starts_at - now).total_seconds()))


class WarningPresenter:
    """Tells the user when the backend warns them, and when their time stops and starts counting again.

    Mirrors the web app's banner. A notification cannot count down, so the warning says how long is
    left, and a reminder follows when only a few seconds remain.
    """

    def __init__(self, notifier: Notifier):
        self._notifier = notifier
        self._state: str | None = None
        self._warned_at: datetime | None = None
        self._reminded = False

    def show(self, state: CompanionState, now: datetime) -> None:
        current = state.state if state.camera_on else None
        if current == "WARNED":
            if self._state != "WARNED" or state.warned_at != self._warned_at:
                self._reminded = False
                self._notifier.notify("You seem to be off task.",
                                      f"Get back to your work: in {_clock(seconds_left(state, now))} this time stops counting.")
            elif not self._reminded and seconds_left(state, now) <= FINAL_REMINDER_SECONDS:
                self._reminded = True
                self._notifier.notify("Still off task?", "This time is about to stop counting.")
        elif current == "DEDUCTING":
            if self._state != "DEDUCTING":
                self._notifier.notify("Off task: this time isn't counting.",
                                      "It counts again as soon as you are back at your work.")
        elif self._state == "DEDUCTING" and current is not None:
            self._notifier.notify("Back on task.", "Your time counts again.")
        self._state = current
        self._warned_at = state.warned_at


def _clock(seconds: int) -> str:
    return f"{seconds // 60}:{seconds % 60:02d}"
