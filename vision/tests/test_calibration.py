import json
import stat
import tempfile
import unittest
from pathlib import Path

from focusquest_companion import calibration
from focusquest_companion.calibration import (ANYWHERE, DEFAULT, SCREEN, SCREEN_OR_DESK, Calibration, HeadPose,
                                              Range, Zone)

#: The screen straight ahead, the desk below it.
ANGLES = Calibration(screen=Zone(Range(-10, 10), Range(0, 10)), desk=Zone(Range(-20, 20), Range(25, 50)))


class LookingAwayTest(unittest.TestCase):

    def test_looking_at_the_screen_is_on_task(self):
        self.assertIsNone(ANGLES.looking_away(HeadPose(5, 5), SCREEN, 0.9))

    def test_a_glance_within_the_margin_is_on_task(self):
        self.assertIsNone(ANGLES.looking_away(HeadPose(19, 5), SCREEN, 0.9))

    def test_turned_well_to_the_side_is_looking_away(self):
        self.assertEqual(ANGLES.looking_away(HeadPose(-50, 5), SCREEN, 0.9), 0.95)

    def test_the_further_away_and_the_surer_the_face_the_surer_it_is(self):
        just_beyond = ANGLES.looking_away(HeadPose(25, 5), SCREEN, 0.9)
        further = ANGLES.looking_away(HeadPose(30, 5), SCREEN, 0.9)
        doubtful_face = ANGLES.looking_away(HeadPose(30, 5), SCREEN, 0.6)
        self.assertLess(just_beyond, further)
        self.assertLess(doubtful_face, further)
        self.assertLess(just_beyond, 0.7)   # below the minimum confidence: not yet counted

    def test_down_at_the_desk_counts_only_where_the_desk_is_part_of_the_work_area(self):
        reading = HeadPose(0, 40)
        self.assertIsNone(ANGLES.looking_away(reading, SCREEN_OR_DESK, 0.9))
        self.assertIsNotNone(ANGLES.looking_away(reading, SCREEN, 0.9))

    def test_turned_away_from_both_is_looking_away_with_the_desk(self):
        self.assertIsNotNone(ANGLES.looking_away(HeadPose(60, 40), SCREEN_OR_DESK, 0.9))

    def test_anywhere_is_never_looking_away(self):
        self.assertIsNone(ANGLES.looking_away(HeadPose(90, 0), ANYWHERE, 0.9))

    def test_diagonal_distance_counts_both_angles(self):
        # 8 degrees out each way is within the margin on either angle alone, but 11.3 beyond the zone in all.
        self.assertIsNotNone(ANGLES.looking_away(HeadPose(18, 18), SCREEN, 1.0))


class ZoneFromPosesTest(unittest.TestCase):

    def test_the_range_seen_without_its_strays(self):
        poses = [HeadPose(yaw, pitch) for yaw, pitch in zip(range(-9, 11), range(0, 20))]
        poses.append(HeadPose(80, -40))   # one stray frame
        zone = Zone.from_poses(poses)
        # Of 21 poses, the most extreme one at each end is left out: the stray, and one at the other end.
        self.assertEqual(zone, Zone(Range(-8, 10), Range(0, 18)))

    def test_too_few_poses_is_refused(self):
        with self.assertRaises(ValueError):
            Zone.from_poses([HeadPose(0, 0)] * (calibration.MIN_SAMPLES - 1))


class StorageTest(unittest.TestCase):

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.path = Path(self.directory.name) / "calibration.json"

    def tearDown(self):
        self.directory.cleanup()

    def test_without_a_calibration_the_default_angles_are_used(self):
        self.assertIs(calibration.load(self.path), DEFAULT)
        self.assertFalse(DEFAULT.calibrated)

    def test_saved_and_loaded_as_angles_only(self):
        calibration.save(self.path, ANGLES)
        self.assertEqual(calibration.load(self.path), ANGLES)
        self.assertEqual(set(json.loads(self.path.read_text())), {"screen", "desk"})
        self.assertEqual(stat.S_IMODE(self.path.stat().st_mode), 0o600)

    def test_kept_next_to_the_pairing(self):
        self.assertEqual(calibration.default_path(Path("/x/companion.json")), Path("/x/calibration.json"))


if __name__ == "__main__":
    unittest.main()
