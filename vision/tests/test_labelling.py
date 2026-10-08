import csv
import io
import re
import unittest
from pathlib import Path

from focusquest_companion import labelling
from focusquest_companion.camera import Readings
from focusquest_companion.detector import AWAY, LOOKING_AWAY, PHONE, Detection
from focusquest_companion.labelling import COLUMNS, Labeller, profile_for

from support import FakeWall, T0

BACKEND = Path(__file__).resolve().parents[2] / "backend" / "src" / "main"


class FakeDetector:
    """Gives the queued detections and readings in turn, and records the profile it was asked with."""

    def __init__(self, looks):
        self.looks = list(looks)
        self.last_readings = None
        self.opened = self.closed = False
        self.profiles = []

    def open(self, now):
        self.opened = True

    def detect(self, now, profile=None):
        self.profiles.append(profile)
        detection, self.last_readings = self.looks.pop(0)
        return detection

    def close(self):
        self.closed = True


class FakeKeys:
    """Keys pressed between looks: one list per second, each key taking no time; then a second passes."""

    def __init__(self, per_second, clock):
        self.per_second = [list(keys) for keys in per_second]
        self.clock = clock

    def __call__(self, seconds):
        if self.per_second and self.per_second[0]:
            return self.per_second[0].pop(0)
        if self.per_second:
            self.per_second.pop(0)
        self.clock.now += seconds
        return None


class Clock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now


ON_TASK = (Detection(), Readings(face_score=0.93, yaw=1.5, pitch=4.0, phone_score=0.0))
TURNED = (Detection({LOOKING_AWAY: 0.85}), Readings(face_score=0.88, yaw=31.2, pitch=3.0, phone_score=0.1))
HOLDING = (Detection({PHONE: 0.9}), Readings(face_score=0.9, yaw=0.0, pitch=20.0, phone_score=0.48,
                                             held_phone_score=0.48, hands=2))
GONE = (Detection({AWAY: 1.0}), Readings(face_score=0.0, phone_score=0.0))


class LabellerTest(unittest.TestCase):

    def label(self, looks, keys, category="CODING"):
        self.detector = FakeDetector(looks)
        self.out = io.StringIO()
        clock = Clock()
        self.shown = []
        labeller = Labeller(self.detector, profile_for(category), self.out, FakeKeys(keys, clock),
                            self.shown.append, wall=FakeWall(T0), monotonic=clock)
        rows = labeller.run()
        return rows, list(csv.reader(io.StringIO(self.out.getvalue())))

    def test_writes_a_row_of_numbers_a_second_until_stopped(self):
        rows, table = self.label([ON_TASK, TURNED, GONE], [[], [], ["q"]])
        self.assertEqual(rows, 3)
        self.assertEqual(table[0], list(COLUMNS))
        self.assertEqual(table[1], ["2026-03-10T09:00:00.000Z", "CODING", "ON_TASK", "", "", "",
                                    "0.93", "1.5", "4.0", "0.0", "", ""])
        self.assertEqual(table[2][3:9], ["", "", "0.85", "0.88", "31.2", "3.0"])
        self.assertEqual(table[3][3:7], ["1.0", "", "", "0.0"])
        self.assertTrue(self.detector.opened and self.detector.closed)

    def test_a_key_changes_the_label_from_the_next_look_on(self):
        _, table = self.label([ON_TASK, HOLDING, HOLDING, GONE, ON_TASK],
                              [["3"], [], ["2"], ["1"], ["Q"]])
        self.assertEqual([row[2] for row in table[1:]], ["ON_TASK", "PHONE", "PHONE", "AWAY", "ON_TASK"])
        self.assertEqual(table[2][4:], ["0.9", "", "0.9", "0.0", "20.0", "0.48", "0.48", "2"])
        self.assertEqual(self.shown, ["ON_TASK", "PHONE", "PHONE", "AWAY", "ON_TASK"])

    def test_other_keys_change_nothing(self):
        _, table = self.label([ON_TASK, ON_TASK], [["x", "5"], ["q"]])
        self.assertEqual([row[2] for row in table[1:]], ["ON_TASK", "ON_TASK"])

    def test_the_last_key_before_a_look_counts(self):
        _, table = self.label([ON_TASK, TURNED, TURNED], [["2", "4"], [], ["q"]])
        self.assertEqual([row[2] for row in table[1:]], ["ON_TASK", "LOOKING_AWAY", "LOOKING_AWAY"])

    def test_looks_with_the_category_profile(self):
        self.label([ON_TASK], [["q"]], category="WRITING")
        self.assertEqual(self.detector.profiles, [profile_for("WRITING")])

    def test_a_camera_that_fails_keeps_the_rows_so_far_and_closes(self):
        with self.assertRaises(IndexError):   # the fake runs out of looks, as a camera might fail
            self.label([ON_TASK, ON_TASK], [[], []])
        self.assertEqual(len(self.out.getvalue().splitlines()), 3)
        self.assertTrue(self.detector.closed)

    def test_only_numbers_labels_and_the_time_are_written(self):
        _, table = self.label([ON_TASK, TURNED, HOLDING, GONE], [["4"], ["3"], ["2"], ["q"]])
        for row in table[1:]:
            self.assertRegex(row[0], r"^\d{4}-\d\d-\d\dT[\d:.]+Z$")
            self.assertIn(row[1], labelling.WORK_AREAS)
            self.assertIn(row[2], labelling.LABELS)
            for cell in row[3:]:
                self.assertRegex(cell, r"^(-?\d+(\.\d+)?)?$")


class ProfileTest(unittest.TestCase):

    def test_the_work_area_decides_whether_looking_away_is_checked(self):
        self.assertEqual(profile_for("CODING").work_area, "SCREEN")
        self.assertEqual(profile_for("CODING").signals, {AWAY, PHONE, LOOKING_AWAY})
        self.assertEqual(profile_for("WRITING").work_area, "SCREEN_OR_DESK")
        self.assertEqual(profile_for("TASK_FREE").signals, {AWAY, PHONE})
        self.assertEqual({check.signal: check.warning_after_seconds for check in profile_for("CODING").checks},
                         {AWAY: 90, PHONE: 5, LOOKING_AWAY: 15})

    def test_the_copy_matches_the_backends_profiles(self):
        java = (BACKEND / "java" / "com" / "example" / "focusquest" / "vision" / "CameraProfiles.java").read_text()
        self.assertEqual(dict(re.findall(r"TaskCategory\.(\w+), WorkArea\.(\w+)", java)), labelling.WORK_AREAS)
        settings = (BACKEND / "resources" / "application.yml").read_text()
        warnings = dict(re.findall(r"^\s+(away|phone|looking-away): (\d+)s$", settings, re.MULTILINE))
        self.assertEqual({"AWAY": int(warnings["away"]), "PHONE": int(warnings["phone"]),
                          "LOOKING_AWAY": int(warnings["looking-away"])}, labelling.WARNING_AFTER_SECONDS)
        self.assertIn(f"grace: {labelling.GRACE_SECONDS}s", settings)
        self.assertIn(f"min-confidence: {labelling.MIN_CONFIDENCE}", settings)


if __name__ == "__main__":
    unittest.main()
