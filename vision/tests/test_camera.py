import math
import unittest
from datetime import timedelta

from focusquest_companion.calibration import Calibration, HeadPose, Range, Zone
from focusquest_companion.camera import CameraDetector, Face, pose_from_matrix, watch_poses
from focusquest_companion.detector import AWAY, LOOKING_AWAY, PHONE, Detection, DetectorUnavailable
from focusquest_companion.phones import Found

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


class FakeFinder:
    """Answers each look with the next queued answer; counts how often it was asked."""

    def __init__(self, answers):
        self.answers = list(answers)
        self.asked = 0
        self.closed = False

    def find(self, frame):
        assert frame is FRAME
        self.asked += 1
        return self.answers.pop(0)

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
        self.poses = FakeFinder(poses)
        detector = CameraDetector(self.camera, lambda: self.faces, poses=lambda: self.poses,
                                  calibration=ANGLES)
        detector.open(T0)
        return detector

    def at(self, seconds):
        return T0 + timedelta(seconds=seconds)

    def test_facing_the_screen_is_on_task_and_turned_away_is_looking_away(self):
        detector = self.detector([face(0.9), face(0.9)], [SCREEN, TURNED])
        self.assertEqual(detector.detect(self.at(0), WRITING), Detection())
        self.assertEqual(detector.detect(self.at(1), WRITING), Detection({LOOKING_AWAY: 1.0}))

    def test_says_where_the_head_pointed_once_as_it_turns_away(self):
        lines = []
        self.camera = FakeCamera([FRAME] * 4)
        detector = CameraDetector(self.camera, lambda: FakeFaces([face(0.9)] * 4),
                                  poses=lambda: FakeFinder([TURNED, TURNED, SCREEN, TURNED]),
                                  calibration=ANGLES, log=lines.append)
        detector.open(T0)
        for second in range(4):
            detector.detect(self.at(second), WRITING)
        self.assertEqual(len(lines), 2)
        self.assertRegex(lines[0], r"^Head turned [+-]\d+, looking down [+-]\d+: outside the work area \(screen .* or desk .*\)\.$")

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
        self.assertEqual(detector.detect(self.at(0), WRITING), Detection({LOOKING_AWAY: 1.0}))
        self.assertEqual(detector.detect(self.at(1), WRITING), Detection({LOOKING_AWAY: 1.0}))
        self.assertEqual(detector.detect(self.at(60), WRITING), Detection({LOOKING_AWAY: 1.0}))
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
        seen = watch_poses(camera, FakeFinder([SCREEN, None, TURNED]), 3, monotonic=lambda: next(ticks))
        self.assertEqual(seen, [SCREEN, TURNED])

    def test_a_camera_that_stops_is_unavailable(self):
        with self.assertRaises(DetectorUnavailable):
            watch_poses(FakeCamera([]), FakeFinder([]), 3, monotonic=lambda: 0)


class PhoneTest(unittest.TestCase):

    HAND = (100, 100, 80, 120)
    IN_HAND = Found(0.5, (150, 150, 60, 100))
    ON_DESK = Found(0.9, (400, 400, 60, 100))

    def detector(self, found, hands, faces=None):
        frames = len(found)
        self.phones = FakeFinder(found)
        self.hands = FakeFinder(hands)
        detector = CameraDetector(FakeCamera([FRAME] * frames), lambda: FakeFaces(faces or [face(0.9)] * frames),
                                  phones=lambda: self.phones, hands=lambda: self.hands)
        detector.open(T0)
        return detector

    def test_a_phone_in_a_hand_is_a_phone(self):
        detector = self.detector([[self.IN_HAND]], [[self.HAND]])
        self.assertEqual(detector.detect(T0, CREATIVE), Detection({PHONE: 0.914}))

    def test_a_phone_on_the_desk_is_on_task(self):
        detector = self.detector([[self.ON_DESK]], [[self.HAND]])
        self.assertEqual(detector.detect(T0, CREATIVE), Detection())

    def test_hands_are_looked_for_only_once_a_phone_is_found(self):
        detector = self.detector([[], [Found(0.2, self.HAND)]], [])
        detector.detect(T0, CREATIVE)
        detector.detect(T0, CREATIVE)
        self.assertEqual(self.hands.asked, 0)

    def test_a_phone_in_a_hand_while_away_from_the_screen_is_both(self):
        detector = self.detector([[self.IN_HAND]], [[self.HAND]], faces=[None])
        self.assertEqual(detector.detect(T0, CREATIVE), Detection({AWAY: 1.0, PHONE: 0.914}))

    def test_without_a_profile_phones_are_not_looked_for(self):
        detector = self.detector([[self.IN_HAND]], [[self.HAND]])
        self.assertEqual(detector.detect(T0, None), Detection())
        self.assertEqual(self.phones.asked, 0)

    def test_closing_closes_the_phone_and_hand_models(self):
        detector = self.detector([], [])
        detector.close()
        self.assertTrue(self.phones.closed)
        self.assertTrue(self.hands.closed)


if __name__ == "__main__":
    unittest.main()
