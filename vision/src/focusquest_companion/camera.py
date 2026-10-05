"""The real detector: a webcam, and models that look at each frame in memory.

OpenCV and MediaPipe are imported only here and only when a camera is used, so the scripted
detector, and the tests, need neither (install them with the ``camera`` extra).

Privacy (requirements specification, sections 19 and 21): a frame lives only in memory, the latest
one replacing the one before, and is dropped when the camera closes. Nothing is written to disk,
uploaded or sent to the backend. The face detector finds that a face is there, not whose it is,
the face landmarker which way the head is turned, and the object detector and hand landmarker
whether a phone is in a hand.
"""
from __future__ import annotations

import math
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import TYPE_CHECKING, Any, Callable, Protocol, Sequence

from . import calibration, phones
from .calibration import Calibration, CalibrationFailed, HeadPose
from .cameras import CameraInfo
from .detector import AWAY, LOOKING_AWAY, PHONE, Detection, DetectorUnavailable
from .phones import Box, Found

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
                f"Cannot open {camera.name}. Check that the companion may use it in System Settings > "
                "Privacy & Security > Camera: it is listed as Python, or as your terminal if you started it there.")
        width, height = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)), int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
        self._log(f"Using {camera.name}, {width} by {height} pixels.")
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
    box: Box


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


class PhoneFinder(Protocol):
    def find(self, frame: Frame) -> list[Found]: ...

    def close(self) -> None: ...


class MediaPipePhones:
    """MediaPipe's object detector (EfficientDet-Lite0, trained on COCO), looking for its "cell phone" class only."""

    def __init__(self, model_path: Path):
        import mediapipe as mp
        from mediapipe.tasks.python import BaseOptions, vision

        self._mp = mp
        # From a little below what counts, so the preview can show the near misses.
        self._detector = vision.ObjectDetector.create_from_options(vision.ObjectDetectorOptions(
            base_options=BaseOptions(model_asset_path=str(model_path)), category_allowlist=["cell phone"],
            score_threshold=0.15, max_results=5))

    def find(self, frame: Frame) -> list[Found]:
        """In the whole frame and in the closer looks (:func:`phones.closer_looks`), each phone once."""
        found = []
        for crop, image in _whole_and_closer(frame):
            found += [Found(phone.score, phones.moved(phone.box, crop)) for phone in self._find(image)]
        return phones.distinct(found)

    def _find(self, image: Frame) -> list[Found]:
        import cv2

        rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        result = self._detector.detect(self._mp.Image(image_format=self._mp.ImageFormat.SRGB, data=rgb))
        return [Found(d.categories[0].score, (d.bounding_box.origin_x, d.bounding_box.origin_y,
                                              d.bounding_box.width, d.bounding_box.height))
                for d in result.detections]

    def close(self) -> None:
        self._detector.close()


class HandFinder(Protocol):
    def find(self, frame: Frame) -> list[Box]: ...

    def close(self) -> None: ...


class MediaPipeHands:
    """MediaPipe's hand landmarker. Each hand becomes the box around its landmarks."""

    def __init__(self, model_path: Path):
        import mediapipe as mp
        from mediapipe.tasks.python import BaseOptions, vision

        self._mp = mp
        # Below the default 0.5: a hand around a phone shows little of its palm, and is often missed at 0.5.
        self._landmarker = vision.HandLandmarker.create_from_options(vision.HandLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=str(model_path)), num_hands=2,
            min_hand_detection_confidence=0.3, min_hand_presence_confidence=0.3))

    def find(self, frame: Frame) -> list[Box]:
        """In the whole frame and in the closer looks: a hand further away is small, too. A hand may be found twice."""
        return [phones.moved(hand, crop) for crop, image in _whole_and_closer(frame) for hand in self._find(image)]

    def _find(self, image: Frame) -> list[Box]:
        import cv2

        height, width = image.shape[:2]
        rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        result = self._landmarker.detect(self._mp.Image(image_format=self._mp.ImageFormat.SRGB, data=rgb))
        boxes = []
        for landmarks in result.hand_landmarks:
            xs = [point.x * width for point in landmarks]
            ys = [point.y * height for point in landmarks]
            boxes.append((int(min(xs)), int(min(ys)), int(max(xs) - min(xs)), int(max(ys) - min(ys))))
        return boxes

    def close(self) -> None:
        self._landmarker.close()


def _whole_and_closer(frame: Frame) -> list[tuple[Box, Frame]]:
    """The whole frame, then each closer look, with where it sits in the frame. The crops are views, not copies."""
    height, width = frame.shape[:2]
    return [((0, 0, width, height), frame)] + [
        ((x, y, w, h), frame[y:y + h, x:x + w]) for x, y, w, h in phones.closer_looks(width, height)]


#: Something to outline in the preview: where, its label, and its colour (BGR).
Mark = tuple[Box, str, tuple[int, int, int]]


class Preview:
    """A window showing what the camera sees and what was found, to try it out. Nothing is saved."""

    TITLE = "FocusQuest camera (nothing is saved)"

    #: The frame width the text sizes are meant for; on a wider picture the text grows with it.
    REFERENCE_WIDTH = 1280
    #: The first line (what is found, or what to do) in yellow, the rest in white (BGR), on a dark band.
    HEADLINE, TEXT = (0, 215, 255), (255, 255, 255)

    def __init__(self, on_top: bool = False, scale: float = 0.7):
        #: In front of other windows: when it opens by itself, from the background, it must be seen.
        self._on_top = on_top
        self._scale = scale
        self._raised = False

    def annotate(self, frame: Frame, marks: Sequence[Mark], lines: Sequence[str]) -> Frame:
        """A copy of ``frame`` with the marks outlined and the lines written on a dark band at the top."""
        import cv2

        shown = frame.copy()
        height, width = shown.shape[:2]
        for (x, y, box_width, box_height), label, colour in marks:
            cv2.rectangle(shown, (x, y), (x + box_width, y + box_height), colour, 2)
            cv2.putText(shown, label, (x, max(20, y - 8)), cv2.FONT_HERSHEY_SIMPLEX, 0.6 * width / self.REFERENCE_WIDTH,
                        colour, 2)
        lines = [line for line in lines if line]
        if not lines:
            return shown
        font = cv2.FONT_HERSHEY_SIMPLEX
        margin = int(0.02 * width)
        scale = self._scale * width / self.REFERENCE_WIDTH
        thickness = max(2, round(2 * scale))
        # Smaller only where a line would not fit across the picture, measured as thick as it is drawn.
        widest = max(cv2.getTextSize(line, font, scale, thickness)[0][0] for line in lines)
        if widest > width - 2 * margin:
            scale *= (width - 2 * margin) / widest
            thickness = max(2, round(2 * scale))
        line_height = int(cv2.getTextSize("Hg", font, scale, thickness)[0][1] * 1.9)
        band = shown[:line_height * len(lines) + margin, :]
        band[:] = (band * 0.25).astype(band.dtype)   # darkened, so the picture still shows through
        for number, line in enumerate(lines):
            colour = self.HEADLINE if number == 0 else self.TEXT
            cv2.putText(shown, line, (margin, line_height * (number + 1)), font, scale, colour, thickness, cv2.LINE_AA)
        return shown

    def show(self, frame: Frame, marks: Sequence[Mark], lines: Sequence[str]) -> None:
        import cv2

        cv2.imshow(self.TITLE, self.annotate(frame, marks, lines))
        if self._on_top and not self._raised:
            try:
                cv2.setWindowProperty(self.TITLE, cv2.WND_PROP_TOPMOST, 1)
            except cv2.error:
                pass   # not every OpenCV build can; the window still opens
            self._raised = True
        cv2.waitKey(1)

    def close(self) -> None:
        import cv2

        try:
            cv2.destroyWindow(self.TITLE)
            cv2.waitKey(1)
        except cv2.error:
            pass   # never shown: the camera closed before its first frame


def watch_poses(camera: Camera, poses: PoseFinder | None, seconds: float, preview: Preview | None = None,
                label: str = "", monotonic: Callable[[], float] = time.monotonic) -> list[HeadPose]:
    """Every head pose seen over ``seconds``, as fast as the camera sends frames. Only the angles are kept.

    Without ``poses`` it only shows the camera, with ``label`` and the seconds left: a countdown.
    """
    seen: list[HeadPose] = []
    end = monotonic() + seconds
    while (now := monotonic()) < end:
        frame = camera.latest()
        if frame is None:
            raise DetectorUnavailable("The camera stopped sending pictures.")
        pose = poses.find(frame) if poses is not None else None
        if pose is not None:
            seen.append(pose)
        if preview is not None:
            left = f"{math.ceil(end - now)}"
            if poses is None:
                preview.show(frame, [], [label, left])
            else:
                found = f"head turned {pose.yaw:+.0f}, looking down {pose.pitch:+.0f}" if pose else "no face found"
                preview.show(frame, [], [label, f"{left} s", found])
    return seen


#: The calibration's steps: which zone, and what to do for it.
CALIBRATION_STEPS = (
    ("screen", "Look at your screen, across all of it"),
    ("desk", "Look down at your desk, as when reading paper"),
)
#: How long each step is announced before its angles are kept, so the user can get there first.
GET_READY_SECONDS = 3


def guided_calibration(camera: Camera, poses: PoseFinder, preview: Preview | None, seconds: float,
                       say: Callable[[str], None] = print,
                       monotonic: Callable[[], float] = time.monotonic) -> Calibration:
    """Screen, then desk, each announced with a countdown in the window, then watched for ``seconds``.

    Raises :class:`CalibrationFailed` if the face was seen too rarely to tell.
    """
    zones = []
    for where, how in CALIBRATION_STEPS:
        say(f"{how}.")
        watch_poses(camera, None, GET_READY_SECONDS, preview, f"Get ready: {how.lower()}", monotonic)
        seen = watch_poses(camera, poses, seconds, preview, f"{how}, and keep looking", monotonic)
        try:
            zones.append(calibration.Zone.from_poses(seen))
        except ValueError as error:
            raise CalibrationFailed(f"{error}. Make sure your face is in view, then calibrate again.") from error
        say(f"Your {where}: {calibration.describe(zones[-1])}.")
    return Calibration(*zones)


class CameraCalibrator:
    """Calibrates with the camera, in a window of its own, and saves the angles (only those) to ``path``.

    Used from the terminal (``calibrate``) and when Settings asks the running program to calibrate.
    ``on_calibrated`` hands the new calibration to the detector, so it takes effect at once.
    """

    def __init__(self, camera: Camera, poses: Callable[[], PoseFinder], path: Path, seconds: float,
                 on_calibrated: Callable[[Calibration], None] = lambda angles: None,
                 preview: Callable[[], Preview | None] = lambda: Preview(on_top=True, scale=1.6),
                 say: Callable[[str], None] = print, monotonic: Callable[[], float] = time.monotonic):
        self._camera = camera
        self._open_poses = poses
        self._path = path
        self._seconds = seconds
        self._on_calibrated = on_calibrated
        self._open_preview = preview
        self._say = say
        self._monotonic = monotonic

    def calibrated_at(self) -> datetime | None:
        """When the saved calibration was made, or None if there is none."""
        if not calibration.load(self._path).calibrated:
            return None
        return datetime.fromtimestamp(self._path.stat().st_mtime, timezone.utc)

    def calibrate(self) -> Calibration:
        self._camera.open()
        window = None
        poses = None
        try:
            poses = self._open_poses()
            window = self._open_preview()
            angles = guided_calibration(self._camera, poses, window, self._seconds, self._say, self._monotonic)
        finally:
            if poses is not None:
                poses.close()
            self._camera.close()
            if window is not None:
                window.close()
        calibration.save(self._path, angles)
        self._on_calibrated(angles)
        return angles


class CameraDetector:
    """Looks at the latest frame when asked.

    - No face is ``AWAY``.
    - Where the profile checks it, a head turned away from the work area is ``LOOKING_AWAY``
      (:meth:`Calibration.looking_away`). The head's angles are worked out only then.
    - A face lost just after the head was seen turned away stays ``LOOKING_AWAY`` until a face is
      found again: a head turned far enough loses the face, and that is not leaving.
    - Where the profile checks it, a phone overlapping a hand is ``PHONE`` (:mod:`.phones`). Hands are
      looked for only once a phone is found.
    """

    def __init__(self, camera: Camera, faces: Callable[[], FaceFinder],
                 poses: Callable[[], PoseFinder] | None = None,
                 phones: Callable[[], PhoneFinder] | None = None,
                 hands: Callable[[], HandFinder] | None = None,
                 calibration: Calibration = calibration.DEFAULT, preview: Preview | None = None,
                 log: Callable[[str], None] = lambda line: None):
        self._camera = camera
        self._open_faces = faces
        self._open_poses = poses
        self._open_phones = phones
        self._open_hands = hands
        self._faces: FaceFinder | None = None
        self._poses: PoseFinder | None = None
        self._phones: PhoneFinder | None = None
        self._hands: HandFinder | None = None
        self._calibration = calibration
        self._preview = preview
        self._log = log
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
            if self._open_phones is not None and self._open_hands is not None:
                self._phones = self._open_phones()
                self._hands = self._open_hands()
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
        work_area = profile.work_area if profile is not None and LOOKING_AWAY in profile.signals else None
        signals, pose = self._head(now, frame, face, work_area)

        checks_phone = profile is not None and PHONE in profile.signals and self._phones is not None
        found: list[Found] = []
        hands: list[Box] = []
        in_hand = None
        if checks_phone:
            found = self._phones.find(frame)
            if any(phone.score >= phones.PHONE_FROM for phone in found):
                hands = self._hands.find(frame)
                in_hand = phones.held(found, hands)
            if in_hand is not None:
                signals[PHONE] = phones.confidence(in_hand)

        detection = Detection(signals)
        if self._preview is not None:
            self._preview.show(frame, _marks(face, found, hands, in_hand),
                               self._describe(detection, pose, work_area, checks_phone))
        return detection

    def _head(self, now: datetime, frame: Frame, face: Face | None,
              work_area: str | None) -> tuple[dict[str, float], HeadPose | None]:
        """Away, or looking away, from the face and the head's angles."""
        score = face.score if face is not None else 0.0
        if score >= PRESENT_FROM:
            self._lost_turned = None
            pose = looking_away = None
            if work_area is not None and self._poses is not None:
                pose = self._poses.find(frame)
                if pose is not None:
                    looking_away = self._calibration.looking_away(pose, work_area, score)
            if looking_away is not None and self._turned is None:
                self._log_turn(pose, work_area)
            self._turned = (now, looking_away) if looking_away is not None else None
            return ({LOOKING_AWAY: looking_away} if looking_away is not None else {}), pose
        if work_area is not None and self._turned is not None and now - self._turned[0] <= LOST_AFTER_TURN:
            self._lost_turned = self._turned[1]
        self._turned = None
        if work_area is not None and self._lost_turned is not None:
            return {LOOKING_AWAY: self._lost_turned}, None
        return {AWAY: round(1 - score, 3)}, None

    def _log_turn(self, pose: HeadPose, work_area: str) -> None:
        """Where the head pointed as it turned away, against the work area: without a preview, the log is how to tell why."""
        zones = " or ".join(f"{name} {calibration.describe(zone)}"
                            for name, zone in (("screen", self._calibration.screen), ("desk", self._calibration.desk))
                            if zone in self._calibration.zones(work_area))
        self._log(f"Head turned {pose.yaw:+.0f}, looking down {pose.pitch:+.0f}: outside the work area ({zones}).")

    def use_calibration(self, angles: Calibration) -> None:
        """A new calibration, from the next look on."""
        self._calibration = angles

    def _describe(self, detection: Detection, pose: HeadPose | None, work_area: str | None,
                  checks_phone: bool) -> list[str]:
        lines = [", ".join(f"{s.lower().replace('_', ' ')} {c:.2f}" for s, c in detection.signals.items()) or "on task"]
        if not checks_phone:
            lines.append("a phone in hand is not checked")
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
        for model in (self._faces, self._poses, self._phones, self._hands):
            if model is not None:
                model.close()
        self._faces = self._poses = self._phones = self._hands = None


GREEN, ORANGE, RED, BLUE, GREY = (0, 200, 0), (0, 160, 255), (0, 0, 230), (230, 140, 0), (160, 160, 160)


def _marks(face: Face | None, found: Sequence[Found], hands: Sequence[Box], in_hand: Found | None) -> list[Mark]:
    marks: list[Mark] = []
    if face is not None:
        marks.append((face.box, f"face {face.score:.2f}", GREEN if face.score >= PRESENT_FROM else ORANGE))
    marks += [(hand, "hand", BLUE) for hand in hands]
    for phone in found:
        colour = RED if phone == in_hand else ORANGE if phone.score >= phones.PHONE_FROM else GREY
        marks.append((phone.box, f"phone {phone.score:.2f}", colour))
    return marks
