"""The real detector: a webcam, and models that look at each frame in memory.

OpenCV and MediaPipe are imported only here and only when a camera is used, so the scripted
detector, and the tests, need neither (install them with the ``camera`` extra).

Privacy (requirements specification, sections 19 and 21): a frame lives only in memory, the latest
one replacing the one before, and is dropped when the camera closes. Nothing is written to disk,
uploaded or sent to the backend. The face detector finds that a face is there, not whose it is.
"""
from __future__ import annotations

import threading
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Protocol

from .cameras import CameraInfo
from .detector import AWAY, Detection, DetectorUnavailable

#: A face found at least this surely means someone is there; below it they count as away.
PRESENT_FROM = 0.5
#: No new frame for this long means the camera stopped working.
FRAME_TIMEOUT_SECONDS = 2.0

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


class Preview:
    """A window showing what the camera sees and what was found, to try it out. Nothing is saved."""

    TITLE = "FocusQuest camera (nothing is saved)"

    def show(self, frame: Frame, face: Face | None, detection: Detection) -> None:
        import cv2

        shown = frame.copy()
        if face is not None:
            x, y, width, height = face.box
            colour = (0, 200, 0) if face.score >= PRESENT_FROM else (0, 160, 255)
            cv2.rectangle(shown, (x, y), (x + width, y + height), colour, 2)
            cv2.putText(shown, f"face {face.score:.2f}", (x, max(20, y - 8)), cv2.FONT_HERSHEY_SIMPLEX, 0.6, colour, 2)
        label = ", ".join(f"{s.lower()} {c:.2f}" for s, c in detection.signals.items()) or "on task"
        cv2.putText(shown, label, (12, 28), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
        cv2.imshow(self.TITLE, shown)
        cv2.waitKey(1)

    def close(self) -> None:
        import cv2

        try:
            cv2.destroyWindow(self.TITLE)
            cv2.waitKey(1)
        except cv2.error:
            pass   # never shown: the camera closed before its first frame


class CameraDetector:
    """Looks at the latest frame when asked. For now it checks presence only: no face is ``AWAY``.

    Looking away and a phone in hand come next (docs/camera_roadmap.md, C1).
    """

    def __init__(self, camera: Camera, faces: Callable[[], FaceFinder], preview: Preview | None = None):
        self._camera = camera
        self._open_faces = faces
        self._faces: FaceFinder | None = None
        self._preview = preview

    def open(self, now: datetime) -> None:
        self._camera.open()
        try:
            self._faces = self._open_faces()
        except Exception:
            self._camera.close()
            raise

    def detect(self, now: datetime) -> Detection:
        if self._faces is None:
            raise RuntimeError("The detector is closed")
        frame = self._camera.latest()
        if frame is None:
            raise DetectorUnavailable("The camera stopped sending pictures.")
        face = self._faces.find(frame)
        score = face.score if face is not None else 0.0
        detection = Detection() if score >= PRESENT_FROM else Detection({AWAY: round(1 - score, 3)})
        if self._preview is not None:
            self._preview.show(frame, face, detection)
        return detection

    def close(self) -> None:
        if self._faces is not None:
            self._faces.close()
            self._faces = None
        self._camera.close()
        if self._preview is not None:
            self._preview.close()
