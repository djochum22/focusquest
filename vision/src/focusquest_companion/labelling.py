"""Labelling: the user says what they are doing while the camera looks, to measure how often it is right.

Once a second the real detector looks, as during a session, and one row of numbers is written: the
time, the label the user gave, what was reported and how surely, and the raw readings behind it
(:class:`~.camera.Readings`). Never a picture (requirements specification, §19). The raw readings let a
new threshold be tried on the same file, without labelling again. The research question is in §21.

There is no backend and no session: the category's profile is built here, from a copy of the backend's.
"""
from __future__ import annotations

import csv
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Protocol, TextIO

from . import calibration
from .api import Check, Profile, format_instant
from .detector import AWAY, LOOKING_AWAY, PHONE, Detection

#: The label while the user is on task; the others are the signals' names.
ON_TASK = "ON_TASK"
LABELS = (ON_TASK, AWAY, PHONE, LOOKING_AWAY)
#: Keys pressed in the camera window, and the label each gives. ``STOP`` ends labelling.
KEYS = {"1": ON_TASK, "2": AWAY, "3": PHONE, "4": LOOKING_AWAY}
STOP = "q"
KEY_HELP = "1 on task, 2 away, 3 phone, 4 looking away, q to stop"

#: A copy of the backend's camera profiles (``CameraProfiles.java`` and ``focusquest.camera.*`` in
#: ``application.yml``); a test checks it against them.
WORK_AREAS = {
    "CODING": calibration.SCREEN, "WORK": calibration.SCREEN, "ADMINISTRATION": calibration.SCREEN,
    "STUDYING": calibration.SCREEN_OR_DESK, "READING": calibration.SCREEN_OR_DESK,
    "WRITING": calibration.SCREEN_OR_DESK, "PLANNING": calibration.SCREEN_OR_DESK,
    "CREATIVE_WORK": calibration.ANYWHERE, "OTHER": calibration.ANYWHERE, "TASK_FREE": calibration.ANYWHERE,
}
WARNING_AFTER_SECONDS = {AWAY: 90, PHONE: 5, LOOKING_AWAY: 15}
GRACE_SECONDS = 60
MIN_CONFIDENCE = 0.7

COLUMNS = ("time", "category", "label", "away", "phone", "looking_away",
           "face_score", "yaw", "pitch", "phone_score", "held_phone_score", "hands")


def profile_for(category: str) -> Profile:
    """The profile a session in ``category`` gets: every category checks away and phone, and looking
    away unless its work area is anywhere."""
    work_area = WORK_AREAS[category]
    signals = (AWAY, PHONE) if work_area == calibration.ANYWHERE else (AWAY, PHONE, LOOKING_AWAY)
    return Profile(category, work_area, tuple(Check(signal, WARNING_AFTER_SECONDS[signal]) for signal in signals),
                   GRACE_SECONDS, MIN_CONFIDENCE)


def default_path(now: datetime) -> Path:
    return Path.home() / "Documents" / "FocusQuest" / f"labels-{now:%Y-%m-%d-%H%M%S}.csv"


class LabelledDetector(Protocol):
    """A :class:`~.camera.CameraDetector`: it looks, and keeps the readings behind its last look."""

    last_readings: object

    def open(self, now: datetime) -> None: ...

    def detect(self, now: datetime, profile: Profile | None = None) -> Detection: ...

    def close(self) -> None: ...


class Labeller:
    """Looks once a second and writes a row each time, until the user presses :data:`STOP`.

    ``next_key`` waits up to the seconds given for a key pressed in the camera window, None if there
    was none; ``show_label`` puts the current label in that window. Each row is flushed as it is
    written, so a camera that fails part way, or Ctrl+C, keeps everything so far.
    """

    def __init__(self, detector: LabelledDetector, profile: Profile, out: TextIO,
                 next_key: Callable[[float], str | None], show_label: Callable[[str], None] = lambda label: None,
                 wall: Callable[[], datetime] = lambda: datetime.now(timezone.utc),
                 monotonic: Callable[[], float] = time.monotonic, interval: float = 1.0):
        self._detector = detector
        self._profile = profile
        self._out = out
        self._writer = csv.writer(out)
        self._next_key = next_key
        self._show_label = show_label
        self._wall = wall
        self._monotonic = monotonic
        self._interval = interval
        self.label = ON_TASK
        #: Rows written so far: one per second looked at.
        self.rows = 0

    def run(self) -> int:
        """Labels until stopped; the number of rows written. Raises what the detector raises."""
        self._writer.writerow(COLUMNS)
        self._out.flush()
        self._detector.open(self._wall())
        try:
            next_look = self._monotonic()
            while True:
                self._show_label(self.label)
                now = self._wall()
                self._write(now, self._detector.detect(now, self._profile))
                next_look = max(next_look + self._interval, self._monotonic())
                if not self._wait_for_keys(next_look):
                    return self.rows
        finally:
            self._detector.close()

    def _wait_for_keys(self, until: float) -> bool:
        """Takes keys until it is time to look again; False once the user stops."""
        while (left := until - self._monotonic()) > 0:
            key = self._next_key(left)
            if key is None:
                continue
            key = key.lower()
            if key == STOP:
                return False
            self.label = KEYS.get(key, self.label)
        return True

    def _write(self, now: datetime, detection: Detection) -> None:
        readings = self._detector.last_readings
        signals = detection.signals
        self._writer.writerow([
            format_instant(now), self._profile.category, self.label,
            *(_number(signals.get(signal)) for signal in (AWAY, PHONE, LOOKING_AWAY)),
            *(_number(getattr(readings, name, None)) for name in COLUMNS[6:]),
        ])
        self._out.flush()
        self.rows += 1


def _number(value: float | int | None) -> str:
    return "" if value is None else str(value)
