import unittest

from focusquest_companion.camera import CameraDetector, Face
from focusquest_companion.detector import AWAY, Detection, DetectorUnavailable

from support import T0

FRAME = object()   # stands in for a picture: the fakes never look inside it


class FakeCamera:
    def __init__(self, frames=(), fails_to_open=False):
        self.frames = list(frames)
        self.fails_to_open = fails_to_open
        self.is_open = False

    def open(self):
        if self.fails_to_open:
            raise DetectorUnavailable("Cannot open camera 0.")
        self.is_open = True

    def latest(self):
        return self.frames.pop(0) if self.frames else None

    def close(self):
        self.is_open = False


class FakeFaces:
    def __init__(self, faces):
        self.faces = list(faces)
        self.closed = False

    def find(self, frame):
        assert frame is FRAME
        return self.faces.pop(0)

    def close(self):
        self.closed = True


def face(score):
    return Face(score, (10, 10, 100, 100))


class CameraDetectorTest(unittest.TestCase):

    def detector(self, faces, frames=None, camera=None):
        self.camera = camera or FakeCamera([FRAME] * (len(faces) if frames is None else frames))
        self.faces = FakeFaces(faces)
        return CameraDetector(self.camera, lambda: self.faces)

    def test_someone_there_is_on_task(self):
        detector = self.detector([face(0.92)])
        detector.open(T0)
        self.assertEqual(detector.detect(T0), Detection())

    def test_no_face_is_away_and_a_doubtful_one_is_away_less_surely(self):
        detector = self.detector([None, face(0.2), face(0.5)])
        detector.open(T0)
        self.assertEqual(detector.detect(T0), Detection({AWAY: 1.0}))
        self.assertEqual(detector.detect(T0), Detection({AWAY: 0.8}))
        self.assertEqual(detector.detect(T0), Detection())

    def test_a_camera_that_sends_nothing_is_unavailable(self):
        detector = self.detector([], frames=0)
        detector.open(T0)
        with self.assertRaises(DetectorUnavailable):
            detector.detect(T0)

    def test_closing_closes_the_camera_and_the_model(self):
        detector = self.detector([])
        detector.open(T0)
        detector.close()
        self.assertFalse(self.camera.is_open)
        self.assertTrue(self.faces.closed)
        with self.assertRaises(RuntimeError):
            detector.detect(T0)

    def test_a_camera_that_cannot_open_is_unavailable(self):
        detector = self.detector([], camera=FakeCamera(fails_to_open=True))
        with self.assertRaises(DetectorUnavailable):
            detector.open(T0)

    def test_the_camera_is_closed_again_if_the_model_fails_to_load(self):
        camera = FakeCamera()

        def broken_model():
            raise OSError("no model")

        detector = CameraDetector(camera, broken_model)
        with self.assertRaises(OSError):
            detector.open(T0)
        self.assertFalse(camera.is_open)


if __name__ == "__main__":
    unittest.main()
