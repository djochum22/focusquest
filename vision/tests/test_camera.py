import math
import unittest
from datetime import timedelta

from focusquest_companion.calibration import Calibration, HeadPose, Range, Zone
from focusquest_companion.camera import CameraDetector, Face, pose_from_matrix, watch_poses
from focusquest_companion.detector import AWAY, LOOKING_AWAY, Detection, DetectorUnavailable

from support import CREATIVE, T0, WRITING

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


class FakePoses:
    def __init__(self, poses):
        self.poses = list(poses)
        self.asked = 0
        self.closed = False

    def find(self, frame):
        assert frame is FRAME
        self.asked += 1
        return self.poses.pop(0)

    def close(self):
        self.closed = True


def face(score):
    return Face(score, (10, 10, 100, 100))


#: The screen straight ahead, the desk below it; WRITING's work area is both.
ANGLES = Calibration(screen=Zone(Range(-10, 10), Range(0, 10)), desk=Zone(Range(-20, 20), Range(25, 50)))
SCREEN = HeadPose(0, 5)
TURNED = HeadPose(60, 5)


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

    def test_without_a_profile_only_presence_is_checked(self):
        detector = self.detector([face(0.9)])
        detector.open(T0)
        self.assertEqual(detector.detect(T0, None), Detection())

    def test_the_camera_is_closed_again_if_the_model_fails_to_load(self):
        camera = FakeCamera()

        def broken_model():
            raise OSError("no model")

        detector = CameraDetector(camera, broken_model)
        with self.assertRaises(OSError):
            detector.open(T0)
        self.assertFalse(camera.is_open)


class LookingAwayTest(unittest.TestCase):

    def detector(self, faces, poses):
        self.camera = FakeCamera([FRAME] * len(faces))
        self.faces = FakeFaces(faces)
        self.poses = FakePoses(poses)
        detector = CameraDetector(self.camera, lambda: self.faces, poses=lambda: self.poses,
                                  calibration=ANGLES)
        detector.open(T0)
        return detector

    def at(self, seconds):
        return T0 + timedelta(seconds=seconds)

    def test_facing_the_screen_is_on_task_and_turned_away_is_looking_away(self):
        detector = self.detector([face(0.9), face(0.9)], [SCREEN, TURNED])
        self.assertEqual(detector.detect(self.at(0), WRITING), Detection())
        self.assertEqual(detector.detect(self.at(1), WRITING), Detection({LOOKING_AWAY: 0.95}))

    def test_reading_at_the_desk_is_on_task_where_the_desk_counts(self):
        detector = self.detector([face(0.9)], [HeadPose(0, 40)])
        self.assertEqual(detector.detect(self.at(0), WRITING), Detection())

    def test_where_looking_away_is_not_checked_the_head_is_not_looked_at(self):
        detector = self.detector([face(0.9)], [])
        self.assertEqual(detector.detect(self.at(0), CREATIVE), Detection())
        self.assertEqual(self.poses.asked, 0)

    def test_a_face_found_without_its_angles_is_on_task(self):
        detector = self.detector([face(0.9)], [None])
        self.assertEqual(detector.detect(self.at(0), WRITING), Detection())

    def test_a_face_lost_just_after_turning_away_is_still_looking_away_until_it_is_back(self):
        detector = self.detector([face(0.9), None, None, face(0.9)], [TURNED, SCREEN])
        self.assertEqual(detector.detect(self.at(0), WRITING), Detection({LOOKING_AWAY: 0.95}))
        self.assertEqual(detector.detect(self.at(1), WRITING), Detection({LOOKING_AWAY: 0.95}))
        self.assertEqual(detector.detect(self.at(60), WRITING), Detection({LOOKING_AWAY: 0.95}))
        self.assertEqual(detector.detect(self.at(61), WRITING), Detection())

    def test_a_face_lost_while_facing_the_screen_is_away(self):
        detector = self.detector([face(0.9), None], [SCREEN])
        detector.detect(self.at(0), WRITING)
        self.assertEqual(detector.detect(self.at(1), WRITING), Detection({AWAY: 1.0}))

    def test_a_face_lost_long_after_the_last_turn_away_is_away(self):
        detector = self.detector([face(0.9), None], [TURNED])
        detector.detect(self.at(0), WRITING)
        self.assertEqual(detector.detect(self.at(5), WRITING), Detection({AWAY: 1.0}))

    def test_reopening_forgets_the_last_turn(self):
        detector = self.detector([face(0.9), None], [TURNED])
        detector.detect(self.at(0), WRITING)
        detector.close()
        self.camera.frames = [FRAME]
        detector.open(self.at(1))
        self.assertEqual(detector.detect(self.at(1), WRITING), Detection({AWAY: 1.0}))

    def test_closing_closes_both_models(self):
        detector = self.detector([], [])
        detector.close()
        self.assertTrue(self.poses.closed)


class PoseFromMatrixTest(unittest.TestCase):

    @staticmethod
    def facing(yaw, pitch):
        """A transformation matrix whose face points ``yaw`` to the picture's right and ``pitch`` down."""
        yaw, pitch = math.radians(yaw), math.radians(pitch)
        forward = (math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch))
        return [[0, 0, forward[0], 0], [0, 0, forward[1], 0], [0, 0, forward[2], -60], [0, 0, 0, 1]]

    def test_straight_at_the_camera(self):
        pose = pose_from_matrix(self.facing(0, 0))
        self.assertAlmostEqual(pose.yaw, 0)
        self.assertAlmostEqual(pose.pitch, 0)

    def test_turned_and_looking_down(self):
        pose = pose_from_matrix(self.facing(30, 20))
        self.assertAlmostEqual(pose.yaw, 30)
        self.assertAlmostEqual(pose.pitch, 20)


class WatchPosesTest(unittest.TestCase):

    def test_keeps_the_angles_seen_for_the_time_given(self):
        ticks = iter([0, 0, 1, 2, 3])   # the start, then once before each look
        camera = FakeCamera([FRAME] * 3)
        seen = watch_poses(camera, FakePoses([SCREEN, None, TURNED]), 3, monotonic=lambda: next(ticks))
        self.assertEqual(seen, [SCREEN, TURNED])

    def test_a_camera_that_stops_is_unavailable(self):
        with self.assertRaises(DetectorUnavailable):
            watch_poses(FakeCamera([]), FakePoses([]), 3, monotonic=lambda: 0)


if __name__ == "__main__":
    unittest.main()
