"""The real detector: a webcam, and models that look at each frame in memory.

OpenCV and MediaPipe are imported only here and only when a camera is used, so the scripted
detector, and the tests, need neither (install them with the ``camera`` extra).

Privacy (requirements specification, sections 19 and 21): a frame lives only in memory, the latest
one replacing the one before, and is dropped when the camera closes. Nothing is written to disk,
uploaded or sent to the backend. The face detector finds that a face is there, not whose it is,
and the face landmarker which way the head is turned.
"""
from __future__ import annotations

import math
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import TYPE_CHECKING, Any, Callable, Protocol, Sequence

from . import calibration
from .calibration import Calibration, HeadPose
from .cameras import CameraInfo
from .detector import AWAY, LOOKING_AWAY, Detection, DetectorUnavailable

if TYPE_CHECKING:
    from .api import Profile

#: A face found at least this surely means someone is there; below it they count as away.
PRESENT_FROM = 0.5
#: No new frame for this long means the camera stopped working.
FRAME_TIMEOUT_SECONDS = 2.0
#: A face lost this soon after the head was seen turned away is still looking away, not away: a head
#: turned far enough loses the face.
LOST_AFTER_TURN = timedelta(seconds=2)

Frame = Any  # a numpy array, height x width x 3, BGR as OpenCV gives it


class Camera(Protocol):
    def open(self) -> None: ...

    def latest(self) -> Frame | None: ...

    def close(self) -> None: ...


class Webcam:
    """Reads frames on a background thread, keeping only the latest one.

    Reading continuously means ``latest`` is a frame from now, not one a camera buffered seconds ago.
    """

    def __init__(self, locate: Callable[[], CameraInfo], log: Callable[[str], None] = print):
        #: Which camera, looked up at each opening: the list can change while the program runs.
        self._locate = locate
        self._log = log
        self._capture: Any = None
        self._frame: Frame | None = None
        self._fresh = threading.Event()
        self._stop = threading.Event()
        self._reader: threading.Thread | None = None

    def open(self) -> None:
        import cv2

        camera = self._locate()
        capture = cv2.VideoCapture(camera.index)
        if not capture.isOpened():
            capture.release()
            raise DetectorUnavailable(
                f"Cannot open {camera.name}. On macOS, allow your terminal to use the camera in "
                "System Settings > Privacy & Security > Camera, then start it again.")
        self._log(f"Using {camera.name}.")
        self._capture = capture
        self._stop.clear()
        self._fresh.clear()
        self._reader = threading.Thread(target=self._read, name="webcam", daemon=True)
        self._reader.start()

    def _read(self) -> None:
        while not self._stop.is_set():
            ok, frame = self._capture.read()
            if not ok:
                break
            self._frame = frame
            self._fresh.set()

    def latest(self) -> Frame | None:
        if not self._fresh.wait(FRAME_TIMEOUT_SECONDS):
            return None
        self._fresh.clear()
        return self._frame

    def close(self) -> None:
        self._stop.set()
        if self._reader is not None:
            self._reader.join(timeout=FRAME_TIMEOUT_SECONDS)
            self._reader = None
        if self._capture is not None:
            self._capture.release()
            self._capture = None
        self._frame = None


@dataclass(frozen=True)
class Face:
    score: float
    #: Where it is in the frame, in pixels: x, y, width, height.
    box: tuple[int, int, int, int]


class FaceFinder(Protocol):
    def find(self, frame: Frame) -> Face | None: ...

    def close(self) -> None: ...


class MediaPipeFaces:
    """MediaPipe's face detector. It reports faces from a low score up, so "barely a face" can be told from "surely one"."""

    def __init__(self, model_path: Path):
        import mediapipe as mp
        from mediapipe.tasks.python import BaseOptions, vision

        self._mp = mp
        self._detector = vision.FaceDetector.create_from_options(vision.FaceDetectorOptions(
            base_options=BaseOptions(model_asset_path=str(model_path)), min_detection_confidence=0.1))

    def find(self, frame: Frame) -> Face | None:
        import cv2

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        result = self._detector.detect(self._mp.Image(image_format=self._mp.ImageFormat.SRGB, data=rgb))
        faces = [Face(d.categories[0].score, (d.bounding_box.origin_x, d.bounding_box.origin_y,
                                              d.bounding_box.width, d.bounding_box.height))
                 for d in result.detections]
        return max(faces, key=lambda face: face.score, default=None)

    def close(self) -> None:
        self._detector.close()


class PoseFinder(Protocol):
    def find(self, frame: Frame) -> HeadPose | None: ...

    def close(self) -> None: ...


class MediaPipePoses:
    """MediaPipe's face landmarker, for its facial transformation matrix: which way the head is turned."""

    def __init__(self, model_path: Path):
        import mediapipe as mp
        from mediapipe.tasks.python import BaseOptions, vision

        self._mp = mp
        self._landmarker = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=str(model_path)), num_faces=1,
            output_facial_transformation_matrixes=True))

    def find(self, frame: Frame) -> HeadPose | None:
        import cv2

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        result = self._landmarker.detect(self._mp.Image(image_format=self._mp.ImageFormat.SRGB, data=rgb))
        if not result.facial_transformation_matrixes:
            return None
        return pose_from_matrix(result.facial_transformation_matrixes[0])

    def close(self) -> None:
        self._landmarker.close()


def pose_from_matrix(matrix: Sequence[Sequence[float]]) -> HeadPose:
    """The head's angles from MediaPipe's facial transformation matrix.

    The matrix turns the face model, which looks along +z, into the camera's space: x to the right
    of the picture, y up, z towards the camera. Its third column is therefore where the face points.
    """
    x, y, z = matrix[0][2], matrix[1][2], matrix[2][2]
    return HeadPose(yaw=math.degrees(math.atan2(x, z)), pitch=math.degrees(math.atan2(-y, math.hypot(x, z))))


class Preview:
    """A window showing what the camera sees and what was found, to try it out. Nothing is saved."""

    TITLE = "FocusQuest camera (nothing is saved)"

    def show(self, frame: Frame, face: Face | None, lines: Sequence[str]) -> None:
        import cv2

        shown = frame.copy()
        if face is not None:
            x, y, width, height = face.box
            colour = (0, 200, 0) if face.score >= PRESENT_FROM else (0, 160, 255)
            cv2.rectangle(shown, (x, y), (x + width, y + height), colour, 2)
            cv2.putText(shown, f"face {face.score:.2f}", (x, max(20, y - 8)), cv2.FONT_HERSHEY_SIMPLEX, 0.6, colour, 2)
        for number, line in enumerate(lines):
            cv2.putText(shown, line, (12, 28 + 26 * number), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
        cv2.imshow(self.TITLE, shown)
        cv2.waitKey(1)

    def close(self) -> None:
        import cv2

        try:
            cv2.destroyWindow(self.TITLE)
            cv2.waitKey(1)
        except cv2.error:
            pass   # never shown: the camera closed before its first frame


def watch_poses(camera: Camera, poses: PoseFinder, seconds: float, preview: Preview | None = None,
                label: str = "", monotonic: Callable[[], float] = time.monotonic) -> list[HeadPose]:
    """Every head pose seen over ``seconds``, as fast as the camera sends frames. Only the angles are kept."""
    seen: list[HeadPose] = []
    end = monotonic() + seconds
    while monotonic() < end:
        frame = camera.latest()
        if frame is None:
            raise DetectorUnavailable("The camera stopped sending pictures.")
        pose = poses.find(frame)
        if pose is not None:
            seen.append(pose)
        if preview is not None:
            found = f"head turned {pose.yaw:+.0f}, looking down {pose.pitch:+.0f}" if pose else "no face"
            preview.show(frame, None, [label, found])
    return seen


class CameraDetector:
    """Looks at the latest frame when asked.

    - No face is ``AWAY``.
    - Where the profile checks it, a head turned away from the work area is ``LOOKING_AWAY``
      (:meth:`Calibration.looking_away`). The head's angles are worked out only then.
    - A face lost just after the head was seen turned away stays ``LOOKING_AWAY`` until a face is
      found again: a head turned far enough loses the face, and that is not leaving.

    A phone in hand comes next (docs/camera_roadmap.md, C1c).
    """

    def __init__(self, camera: Camera, faces: Callable[[], FaceFinder],
                 poses: Callable[[], PoseFinder] | None = None,
                 calibration: Calibration = calibration.DEFAULT, preview: Preview | None = None):
        self._camera = camera
        self._open_faces = faces
        self._open_poses = poses
        self._faces: FaceFinder | None = None
        self._poses: PoseFinder | None = None
        self._calibration = calibration
        self._preview = preview
        #: When the head was last seen turned away, and how surely; None once seen otherwise.
        self._turned: tuple[datetime, float] | None = None
        #: How surely, while the face is lost after a turn away.
        self._lost_turned: float | None = None

    def open(self, now: datetime) -> None:
        self._camera.open()
        try:
            self._faces = self._open_faces()
            if self._open_poses is not None:
                self._poses = self._open_poses()
        except Exception:
            self._close_models()
            self._camera.close()
            raise
        self._turned = self._lost_turned = None

    def detect(self, now: datetime, profile: Profile | None = None) -> Detection:
        if self._faces is None:
            raise RuntimeError("The detector is closed")
        frame = self._camera.latest()
        if frame is None:
            raise DetectorUnavailable("The camera stopped sending pictures.")
        face = self._faces.find(frame)
        score = face.score if face is not None else 0.0
        work_area = profile.work_area if profile is not None and LOOKING_AWAY in profile.signals else None
        pose = None
        if score >= PRESENT_FROM:
            self._lost_turned = None
            looking_away = None
            if work_area is not None and self._poses is not None:
                pose = self._poses.find(frame)
                if pose is not None:
                    looking_away = self._calibration.looking_away(pose, work_area, score)
            self._turned = (now, looking_away) if looking_away is not None else None
            detection = Detection({LOOKING_AWAY: looking_away}) if looking_away is not None else Detection()
        else:
            if work_area is not None and self._turned is not None and now - self._turned[0] <= LOST_AFTER_TURN:
                self._lost_turned = self._turned[1]
            self._turned = None
            if work_area is not None and self._lost_turned is not None:
                detection = Detection({LOOKING_AWAY: self._lost_turned})
            else:
                detection = Detection({AWAY: round(1 - score, 3)})
        if self._preview is not None:
            self._preview.show(frame, face, self._describe(detection, pose, work_area))
        return detection

    def _describe(self, detection: Detection, pose: HeadPose | None, work_area: str | None) -> list[str]:
        lines = [", ".join(f"{s.lower().replace('_', ' ')} {c:.2f}" for s, c in detection.signals.items()) or "on task"]
        if work_area is None:
            return lines + ["where you look is not checked"]
        if pose is not None:
            lines.append(f"head turned {pose.yaw:+.0f}, looking down {pose.pitch:+.0f}")
        names = {calibration.SCREEN: "screen", calibration.SCREEN_OR_DESK: "screen or desk"}
        lines.append(f"work area: {names.get(work_area, work_area.lower())}, "
                     + ("calibrated" if self._calibration.calibrated else "not calibrated (run `calibrate`)"))
        for name, zone in (("screen", self._calibration.screen), ("desk", self._calibration.desk)):
            if zone in self._calibration.zones(work_area):
                lines.append(f"{name}: {calibration.describe(zone)}")
        return lines

    def close(self) -> None:
        self._close_models()
        self._camera.close()
        if self._preview is not None:
            self._preview.close()

    def _close_models(self) -> None:
        for model in (self._faces, self._poses):
            if model is not None:
                model.close()
        self._faces = self._poses = None
