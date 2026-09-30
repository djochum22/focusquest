"""Which camera to use, by name, turned into the number OpenCV opens it by.

On macOS, OpenCV numbers cameras in the order of their unique IDs (cap_avfoundation_mac.mm sorts
them so). ``system_profiler`` lists each camera's name and unique ID, so sorting it the same way gives
each name its number. The list can change while the program runs, when an iPhone connects as a
Continuity Camera for example, so a choice is looked up again each time the camera opens.
"""
from __future__ import annotations

import json
import platform
import subprocess
from dataclasses import dataclass
from typing import Callable

from .detector import DetectorUnavailable

#: Words in the name of a Mac's own camera. An iPhone's (Continuity Camera) has none of them.
BUILT_IN_NAMES = ("macbook", "facetime", "imac", "built-in")


@dataclass(frozen=True)
class CameraInfo:
    index: int
    name: str
    unique_id: str


def list_cameras(run: Callable[..., subprocess.CompletedProcess] = subprocess.run) -> list[CameraInfo] | None:
    """The cameras in OpenCV's order, or None where they cannot be listed (not macOS)."""
    if platform.system() != "Darwin":
        return None
    try:
        result = run(["system_profiler", "SPCameraDataType", "-json"], capture_output=True, text=True,
                     timeout=15, check=True)
        entries = json.loads(result.stdout).get("SPCameraDataType", [])
    except (OSError, subprocess.SubprocessError, ValueError):
        return None
    cameras = sorted((entry.get("spcamera_unique-id", ""), entry.get("_name", "Camera")) for entry in entries)
    return [CameraInfo(index, name, unique_id) for index, (unique_id, name) in enumerate(cameras)]


@dataclass(frozen=True)
class CameraChoice:
    """``None`` for the built-in camera, a number for OpenCV's index, or part of a camera's name."""

    wanted: str | None = None

    @classmethod
    def parse(cls, text: str | None) -> CameraChoice:
        return cls(None if text is None or text.strip().lower() in ("", "built-in") else text.strip())

    def describe(self) -> str:
        if self.wanted is None:
            return "the built-in camera"
        return f"camera {self.wanted}" if self.wanted.isdigit() else f"the camera named like {self.wanted!r}"

    def resolve(self, cameras: list[CameraInfo] | None) -> CameraInfo:
        """The camera this choice means among ``cameras``, with OpenCV's index for it."""
        if self.wanted is not None and self.wanted.isdigit():
            index = int(self.wanted)
            listed = next((c for c in cameras or [] if c.index == index), None)
            return listed or CameraInfo(index, f"camera {index}", "")
        if cameras is None:
            if self.wanted is None:
                return CameraInfo(0, "camera 0", "")   # cannot be named here; the first is usually the computer's own
            raise DetectorUnavailable("Cameras can only be chosen by name on macOS; give a number instead.")
        if self.wanted is None:
            match = next((c for c in cameras if any(word in c.name.lower() for word in BUILT_IN_NAMES)), None)
            if match is None:
                raise DetectorUnavailable(f"No built-in camera found{_among(cameras)}. Choose one with --camera NAME.")
        else:
            match = next((c for c in cameras if self.wanted.lower() in c.name.lower()), None)
            if match is None:
                raise DetectorUnavailable(f"No camera named like {self.wanted!r}{_among(cameras)}.")
        return match


def _among(cameras: list[CameraInfo]) -> str:
    return f" among: {', '.join(c.name for c in cameras)}" if cameras else " (no cameras found)"
