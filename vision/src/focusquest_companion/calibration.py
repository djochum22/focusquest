"""Where the user looks while on task, and the looking-away rule.

Every camera sits somewhere different, so the angles that mean "at the screen" or "down at the desk"
come from a calibration: the user looks at the screen for a few seconds, then down at the desk, and
the range of head angles seen is kept. Only those angles are stored, never a picture. Without a
calibration, fixed angles around straight ahead are used.
"""
from __future__ import annotations

import json
import math
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

#: The work areas, as the backend names them (requirements specification, section 21).
SCREEN = "SCREEN"
SCREEN_OR_DESK = "SCREEN_OR_DESK"
ANYWHERE = "ANYWHERE"

#: How far beyond a zone, in degrees, the head may turn and still be on task: a glance is not looking away.
#: The calibration already takes in the whole screen, so this only allows for a little wobble.
MARGIN_DEGREES = 5.0
#: Beyond the margin, how many more degrees until looking away is certain.
CERTAIN_AFTER_DEGREES = 15.0
#: The confidence just beyond the margin, for a clearly seen face: the backend's minimum (0.7 today), so it counts.
LEAST_CONFIDENCE = 0.7
#: A face found at least this surely is clearly seen; a less sure one lowers the confidence in proportion.
CLEAR_FACE_FROM = 0.7
#: A calibration needs at least this many head poses for each zone.
MIN_SAMPLES = 10
#: The share of poses left out at each end of a range, so one stray frame does not widen it.
TRIM = 0.05


@dataclass(frozen=True)
class HeadPose:
    """Which way the head is turned, in degrees. Straight at the camera is 0 and 0.

    ``yaw`` is positive turned towards the right of the picture (the user's left, as the camera faces
    them), ``pitch`` is positive looking down.
    """

    yaw: float
    pitch: float


@dataclass(frozen=True)
class Range:
    low: float
    high: float

    def outside(self, value: float) -> float:
        """How far ``value`` is outside the range, 0 if inside."""
        return max(self.low - value, value - self.high, 0.0)


@dataclass(frozen=True)
class Zone:
    """A region the head may point at: the screen, or the desk."""

    yaw: Range
    pitch: Range

    def outside(self, pose: HeadPose) -> float:
        """How far the head points outside the zone, in degrees, 0 if inside."""
        return math.hypot(self.yaw.outside(pose.yaw), self.pitch.outside(pose.pitch))

    @classmethod
    def from_poses(cls, poses: Sequence[HeadPose]) -> Zone:
        """The range of angles seen, leaving out the most extreme few at each end."""
        if len(poses) < MIN_SAMPLES:
            raise ValueError(f"Saw your face {len(poses)} times; at least {MIN_SAMPLES} are needed")
        return cls(_trimmed_range([p.yaw for p in poses]), _trimmed_range([p.pitch for p in poses]))


@dataclass(frozen=True)
class Calibration:
    screen: Zone
    desk: Zone
    #: False for the fixed angles used until the user calibrates.
    calibrated: bool = True

    def zones(self, work_area: str) -> tuple[Zone, ...]:
        if work_area == SCREEN:
            return (self.screen,)
        if work_area == SCREEN_OR_DESK:
            return (self.screen, self.desk)
        return ()

    def looking_away(self, pose: HeadPose, work_area: str, face_score: float) -> float | None:
        """The confidence that the head is turned away from the work area, or None if it is not.

        Just beyond the margin it is :data:`LEAST_CONFIDENCE`, growing to 1 over the next
        :data:`CERTAIN_AFTER_DEGREES`. A face found less surely than :data:`CLEAR_FACE_FROM` lowers it.
        """
        zones = self.zones(work_area)
        if not zones:
            return None
        beyond = min(zone.outside(pose) for zone in zones) - MARGIN_DEGREES
        if beyond <= 0:
            return None
        certainty = LEAST_CONFIDENCE + (1 - LEAST_CONFIDENCE) * min(1.0, beyond / CERTAIN_AFTER_DEGREES)
        return round(certainty * min(1.0, face_score / CLEAR_FACE_FROM), 3)


#: Until the user calibrates: the screen roughly straight ahead (a laptop camera sits just above it),
#: the desk below it.
DEFAULT = Calibration(
    screen=Zone(Range(-15.0, 15.0), Range(-10.0, 20.0)),
    desk=Zone(Range(-20.0, 20.0), Range(20.0, 55.0)),
    calibrated=False,
)


def _trimmed_range(values: Sequence[float]) -> Range:
    ordered = sorted(values)
    cut = int(len(ordered) * TRIM)
    kept = ordered[cut:len(ordered) - cut]
    return Range(round(kept[0], 1), round(kept[-1], 1))


def default_path(config_path: Path) -> Path:
    """Kept next to the pairing, in its own file: pairing again does not lose it."""
    return config_path.with_name("calibration.json")


def load(path: Path) -> Calibration:
    """The saved calibration, or :data:`DEFAULT` if there is none."""
    try:
        data = json.loads(path.read_text())
    except FileNotFoundError:
        return DEFAULT
    return Calibration(_zone_from_json(data["screen"]), _zone_from_json(data["desk"]))


def save(path: Path, calibration: Calibration) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = {"screen": _zone_to_json(calibration.screen), "desk": _zone_to_json(calibration.desk)}
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "w") as file:
        json.dump(data, file, indent=2)


def describe(zone: Zone) -> str:
    # Plain degrees, no sign: the preview's font cannot draw it.
    return (f"turned {zone.yaw.low:+.0f} to {zone.yaw.high:+.0f}, "
            f"looking down {zone.pitch.low:+.0f} to {zone.pitch.high:+.0f} degrees")


def _zone_to_json(zone: Zone) -> dict:
    return {"yaw": [zone.yaw.low, zone.yaw.high], "pitch": [zone.pitch.low, zone.pitch.high]}


def _zone_from_json(data: dict) -> Zone:
    return Zone(Range(*map(float, data["yaw"])), Range(*map(float, data["pitch"])))
