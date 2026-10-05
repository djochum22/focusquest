"""What looks at the user, and a scripted stand-in for it.

A detector turns what the camera sees at one moment into the off-task signals it sees, each with a
confidence. The real detector (OpenCV and MediaPipe) goes behind the same interface; until then the
:class:`ScriptedDetector` replays a timeline, so the whole pipeline can run without a camera.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import TYPE_CHECKING, Mapping, Protocol, Sequence

if TYPE_CHECKING:
    from .api import Profile

#: The off-task signals, as the backend names them.
AWAY = "AWAY"
PHONE = "PHONE"
LOOKING_AWAY = "LOOKING_AWAY"
SIGNALS = (AWAY, PHONE, LOOKING_AWAY)


@dataclass(frozen=True)
class Detection:
    """The off-task signals seen at one moment, each with its confidence (0 to 1). Empty: on task."""

    signals: Mapping[str, float] = field(default_factory=dict)


class DetectorUnavailable(Exception):
    """The detector cannot see: no camera, no permission to use it, or no frames coming from it."""


class Detector(Protocol):
    """Opened while a camera-verified session runs, and closed as soon as it stops.

    Frames exist only between ``open`` and ``close``, in memory: never saved, uploaded or sent.
    ``open`` and ``detect`` raise :class:`DetectorUnavailable` when there is nothing to see.
    ``detect`` is given the session's profile, for what it checks and where the work area is.
    """

    def open(self, now: datetime) -> None: ...

    def detect(self, now: datetime, profile: Profile | None = None) -> Detection: ...

    def close(self) -> None: ...


@dataclass(frozen=True)
class Step:
    """One stretch of a script: a signal (None while present and on task, ``UNAVAILABLE`` while the
    camera cannot be used) for a duration."""

    signal: str | None
    duration: timedelta
    confidence: float = 0.9


#: A script step during which the camera cannot be used, as when another app has taken it.
UNAVAILABLE = "UNAVAILABLE"

_STEP = re.compile(r"^(present|away|phone|looking-away|unavailable)\s+(\d+)\s*(s|m|h)(?:\s*@\s*([01](?:\.\d+)?))?$")
_UNITS = {"s": 1, "m": 60, "h": 3600}
_SIGNAL_NAMES = {"present": None, "away": AWAY, "phone": PHONE, "looking-away": LOOKING_AWAY,
                 "unavailable": UNAVAILABLE}


def parse_script(text: str) -> list[Step]:
    """Reads a script such as ``"present 60s, phone 90s, away 3m @0.8"``.

    Each step is ``present``, ``away``, ``phone``, ``looking-away`` or ``unavailable`` (the camera
    cannot be used), a duration in s, m or h, and optionally ``@`` a confidence (0.9 by default).
    """
    steps = []
    for part in text.split(","):
        match = _STEP.match(part.strip().lower())
        if not match:
            raise ValueError(f"Cannot read the step {part.strip()!r}: expected, for example, 'phone 90s' or 'away 3m @0.8'")
        name, amount, unit, confidence = match.groups()
        duration = timedelta(seconds=int(amount) * _UNITS[unit])
        if duration <= timedelta(0):
            raise ValueError(f"The step {part.strip()!r} must last longer than nothing")
        step = Step(_SIGNAL_NAMES[name], duration, *([float(confidence)] if confidence is not None else []))
        if not 0 <= step.confidence <= 1:
            raise ValueError(f"The confidence in {part.strip()!r} must be between 0 and 1")
        steps.append(step)
    return steps


class ScriptedDetector:
    """Replays a timeline from the moment it is opened, and opens no camera.

    After the last step it sees the user present and on task, or with ``loop`` starts over. Each
    opening starts the timeline again: pausing and resuming a session replays it from the start.

    During an ``unavailable`` step it raises :class:`DetectorUnavailable`, like a camera another app
    has taken. The companion then closes it and tries to open it again at each report; those openings
    fail until the step is over and then carry on with the same timeline instead of starting it again.
    """

    def __init__(self, steps: Sequence[Step], loop: bool = False):
        if not steps:
            raise ValueError("A script needs at least one step")
        self._steps = tuple(steps)
        self._loop = loop
        self._total = sum((step.duration for step in steps), timedelta(0))
        self._opened_at: datetime | None = None
        #: The script made the camera unavailable: closing keeps the timeline, to carry on after it.
        self._failed = False

    def open(self, now: datetime) -> None:
        if self._failed and self._opened_at is not None:
            if self._step_at(now) is UNAVAILABLE:
                raise DetectorUnavailable("The scripted camera is unavailable.")
            self._failed = False
            return
        self._failed = False
        self._opened_at = now

    def close(self) -> None:
        if not self._failed:
            self._opened_at = None

    def detect(self, now: datetime, profile: Profile | None = None) -> Detection:
        if self._opened_at is None:
            raise RuntimeError("The detector is closed")
        step = self._step_at(now)
        if step is UNAVAILABLE:
            self._failed = True
            raise DetectorUnavailable("The scripted camera is unavailable.")
        return Detection({step.signal: step.confidence}) if step and step.signal else Detection()

    def _step_at(self, now: datetime) -> Step | str | None:
        """The step under way, ``UNAVAILABLE`` during an unavailable one, or None once the script is over."""
        elapsed = max(now - self._opened_at, timedelta(0))
        if elapsed >= self._total:
            if not self._loop:
                return None
            elapsed %= self._total
        for step in self._steps:
            if elapsed < step.duration:
                return UNAVAILABLE if step.signal == UNAVAILABLE else step
            elapsed -= step.duration
        return None
