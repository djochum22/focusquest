import contextlib
import io
import tempfile
import unittest
from datetime import timedelta
from pathlib import Path

from focusquest_companion import accuracy
from focusquest_companion.accuracy import Episode, Report, Row, Thresholds, episodes, read, replay
from focusquest_companion.api import Observation
from focusquest_companion.calibration import HeadPose
from focusquest_companion.camera import CameraDetector
from focusquest_companion.cli import main
from focusquest_companion.detector import AWAY, LOOKING_AWAY, PHONE
from focusquest_companion.labelling import COLUMNS, Labeller, profile_for
from focusquest_companion.phones import Found

from support import FakeWall, T0
from test_camera import ANGLES, FRAME, SCREEN, TURNED, FakeCamera, FakeFaces, FakeFinder, face
from test_labelling import Clock, FakeKeys

CODING = profile_for("CODING")
HAND = (100, 100, 80, 120)


def at(second):
    return T0 + timedelta(seconds=second)


def row(second, label="ON_TASK", face_score=0.9, pose=SCREEN, held=None, category="CODING", reported=None):
    """A recorded second; ``pose`` None for a face whose angles were not found."""
    seen = face_score >= 0.5 and pose is not None
    return Row(at(second), category, label, reported or {}, face_score,
               pose.yaw if seen else None, pose.pitch if seen else None, held)


def seconds(*runs, category="CODING"):
    """Rows a second apart from runs of (count, label, keyword arguments for :func:`row`)."""
    rows = []
    for count, label, readings in runs:
        rows += [row(len(rows) + second, label, category=category, **readings) for second in range(count)]
    return rows


ON = {}
GONE = {"face_score": 0.0}
FAINT = {"face_score": 0.4}
LOOKING = {"pose": TURNED}
HOLDING = {"held": 0.5}


def report(rows, thresholds=Thresholds()):
    result = Report(thresholds)
    result.add(rows, ANGLES)
    return result


class ReplayTest(unittest.TestCase):

    def test_today_s_thresholds_give_what_the_camera_reported_while_labelling(self):
        looks = [(face(0.9), SCREEN, [], []), (face(0.9), TURNED, [], []), (None, None, [], []),
                 (None, None, [], []), (None, None, [], []), (face(0.6), SCREEN, [Found(0.5, HAND)], [HAND]),
                 (face(0.3), None, [Found(0.3, (500, 500, 10, 10))], [HAND]), (None, None, [], [])]
        faces, poses, found, hands = zip(*looks)
        detector = CameraDetector(
            FakeCamera([FRAME] * len(looks)), lambda: FakeFaces(faces),
            poses=lambda: FakeFinder([pose for pose in poses if pose is not None]),
            phones=lambda: FakeFinder(found), hands=lambda: FakeFinder([h for f, h in zip(found, hands) if f]),
            calibration=ANGLES)
        out = io.StringIO()
        clock = Clock()
        Labeller(detector, CODING, out, FakeKeys([[]] * (len(looks) - 1) + [["q"]], clock),
                 wall=FakeWall(T0), monotonic=clock).run()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "labels.csv"
            path.write_text(out.getvalue())
            rows = read(path)
        replayed = replay(rows, CODING, ANGLES, Thresholds())
        self.assertEqual(accuracy.differences(rows, replayed), 0)
        for recorded, signals in zip(rows, replayed):
            self.assertEqual(signals.keys(), recorded.reported.keys())
            for signal, confidence in signals.items():   # rounded when written
                self.assertAlmostEqual(confidence, recorded.reported[signal], delta=0.011)
        self.assertEqual([set(signals) for signals in replayed],
                         [set(), {LOOKING_AWAY}, {LOOKING_AWAY}, {LOOKING_AWAY}, {LOOKING_AWAY}, {PHONE}, {AWAY},
                          {AWAY}])

    def test_a_face_lost_just_after_a_turn_stays_looking_away_for_a_while(self):
        rows = seconds((1, "ON_TASK", LOOKING), (7, "ON_TASK", GONE), (1, "ON_TASK", ON), (1, "ON_TASK", GONE))
        self.assertEqual([set(signals) for signals in replay(rows, CODING, ANGLES, Thresholds())],
                         [{LOOKING_AWAY}] * 7 + [{AWAY}, set(), {AWAY}])
        self.assertEqual([set(signals) for signals in replay(rows, CODING, ANGLES, Thresholds(lost_for=2))][:5],
                         [{LOOKING_AWAY}] * 4 + [{AWAY}])

    def test_a_wider_margin_lets_a_turn_pass(self):
        rows = [row(0, pose=HeadPose(18, 5))]   # 8 degrees beyond the screen
        self.assertIn(LOOKING_AWAY, replay(rows, CODING, ANGLES, Thresholds())[0])
        self.assertEqual(replay(rows, CODING, ANGLES, Thresholds(margin=10))[0], {})

    def test_a_raised_presence_threshold_makes_a_faint_face_away(self):
        rows = [row(0, face_score=0.55)]
        self.assertEqual(replay(rows, CODING, ANGLES, Thresholds())[0], {})
        self.assertEqual(replay(rows, CODING, ANGLES, Thresholds(present_from=0.6))[0], {AWAY: 0.725})

    def test_the_phone_thresholds_are_tried_on_the_phone_in_a_hand(self):
        rows = [row(0, held=0.3)]
        self.assertEqual(replay(rows, CODING, ANGLES, Thresholds())[0], {PHONE: 0.743})
        self.assertEqual(replay(rows, CODING, ANGLES, Thresholds(phone_from=0.35))[0], {})
        self.assertEqual(replay(rows, CODING, ANGLES, Thresholds(certain_from=0.3))[0], {PHONE: 1.0})

    def test_looking_away_only_where_the_category_checks_it(self):
        rows = [row(0, pose=TURNED, category="TASK_FREE")]
        self.assertEqual(replay(rows, profile_for("TASK_FREE"), ANGLES, Thresholds())[0], {})

    def test_thresholds_the_recorded_readings_cannot_answer_are_refused(self):
        self.assertIsNone(Thresholds(margin=8, phone_from=0.3).problem())
        self.assertIn("angles were not recorded", Thresholds(present_from=0.4).problem())
        self.assertIn("hands were not looked for", Thresholds(phone_from=0.2).problem())
        self.assertIn("above --phone-from", Thresholds(phone_from=0.5, certain_from=0.4).problem())


class EpisodesTest(unittest.TestCase):

    def observe(self, signal, start, end, confidence=0.9):
        return Observation(f"{signal}{start}", signal, confidence, at(start), at(end))

    def episodes(self, *observed):
        return episodes(observed, CODING, 0.7, timedelta(seconds=5), at(1000))

    def test_warned_once_one_signal_has_lasted_its_warning_time(self):
        self.assertEqual(self.episodes(self.observe(PHONE, 10, 20)), [Episode(at(10), at(20), at(15), PHONE)])
        self.assertEqual(self.episodes(self.observe(PHONE, 10, 14)), [Episode(at(10), at(14), None, None)])

    def test_gaps_of_up_to_five_seconds_are_one_stretch(self):
        found = self.episodes(self.observe(LOOKING_AWAY, 0, 8), self.observe(LOOKING_AWAY, 13, 20))
        self.assertEqual(found, [Episode(at(0), at(20), at(15), LOOKING_AWAY)])
        found = self.episodes(self.observe(LOOKING_AWAY, 0, 8), self.observe(LOOKING_AWAY, 14, 20))
        self.assertEqual([episode.warned_at for episode in found], [None, None])

    def test_signals_do_not_add_up(self):
        found = self.episodes(self.observe(PHONE, 0, 4), self.observe(LOOKING_AWAY, 4, 14))
        self.assertEqual(found, [Episode(at(0), at(14), None, None)])

    def test_less_sure_than_the_minimum_and_single_looks_are_ignored(self):
        self.assertEqual(self.episodes(self.observe(PHONE, 0, 20, confidence=0.69), self.observe(PHONE, 30, 30)), [])


class ReportTest(unittest.TestCase):

    def test_counts_each_second_leaving_out_the_two_after_a_label_change(self):
        rows = seconds((10, "ON_TASK", ON), (2, "LOOKING_AWAY", ON), (8, "LOOKING_AWAY", LOOKING),
                       (3, "ON_TASK", LOOKING), (7, "ON_TASK", ON))
        result = report(rows)
        self.assertEqual(result.left_out, 4)
        looking = result.per_second[LOOKING_AWAY]
        self.assertEqual((looking.labelled, looking.found, looking.reported), (8, 8, 9))
        self.assertEqual((looking.on_task, looking.false), (18, 1))
        self.assertEqual(result.seconds["ON_TASK"], 20)

    def test_a_long_enough_stretch_warned_about_in_time(self):
        rows = seconds((5, "ON_TASK", ON), (12, "PHONE", HOLDING), (5, "ON_TASK", ON))
        warnings = report(rows).warnings[PHONE]
        self.assertEqual((warnings.long_enough, warnings.warned, warnings.delays, warnings.false), (1, 1, [5.0], 0))

    def test_a_warning_while_on_task_is_false(self):
        rows = seconds((30, "ON_TASK", ON), (20, "ON_TASK", LOOKING))
        warnings = report(rows).warnings[LOOKING_AWAY]
        self.assertEqual((warnings.false, warnings.long_enough), (1, 0))

    def test_a_stretch_too_short_to_warn_is_counted_apart(self):
        rows = seconds((5, "ON_TASK", ON), (3, "PHONE", HOLDING), (5, "ON_TASK", ON))
        warnings = report(rows).warnings[PHONE]
        self.assertEqual((warnings.shorter, warnings.long_enough), (1, 0))

    def test_a_faint_face_while_away_still_counts(self):
        rows = seconds((5, "ON_TASK", ON), (50, "AWAY", GONE), (1, "AWAY", FAINT), (50, "AWAY", GONE),
                       (5, "ON_TASK", ON))
        result = report(rows)
        self.assertEqual(result.per_second[AWAY].found, 99)   # the first 2 left out
        warnings = result.warnings[AWAY]
        self.assertEqual((warnings.long_enough, warnings.warned, warnings.delays, warnings.too_unsure),
                         (1, 1, [90.0], 0))

    def test_a_stretch_less_sure_than_the_minimum_is_ignored_and_said_so(self):
        rows = seconds((5, "ON_TASK", ON), (20, "PHONE", {"held": 0.4}), (5, "ON_TASK", ON))
        result = report(rows, Thresholds(min_confidence=0.9))
        self.assertEqual((result.warnings[PHONE].warned, result.warnings[PHONE].too_unsure), (0, 20))
        self.assertIn("Phone: 20 of the 20 seconds sent", "\n".join(accuracy.format_report(result)))

    def test_the_report_says_when_there_is_too_little_on_task(self):
        lines = accuracy.format_report(report(seconds((60, "ON_TASK", ON))))
        self.assertIn("Only 1.0 minutes on task", lines[-1])


class AccuracyCommandTest(unittest.TestCase):

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = Path(directory.name)

    def run_cli(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = main(["--config", str(self.directory / "companion.json"), "accuracy", *args])
        return code, out.getvalue(), err.getvalue()

    def test_reports_on_the_files_given(self):
        path = self.directory / "labels.csv"
        path.write_text(",".join(COLUMNS) + "\n2026-03-10T09:00:00.000Z,CODING,ON_TASK,,,,0.9,1.0,2.0,0.0,,\n")
        code, out, _ = self.run_cli(str(path), "--margin", "7")
        self.assertEqual(code, 0)
        self.assertIn("1 file, 1 seconds labelled: 1 on task", out)
        self.assertIn("looking away from 7 degrees", out)

    def test_another_file_is_refused(self):
        path = self.directory / "other.csv"
        path.write_text("a,b\n1,2\n")
        code, _, err = self.run_cli(str(path))
        self.assertEqual(code, 1)
        self.assertIn("not a file written by `label`", err)

    def test_thresholds_the_files_cannot_answer_are_refused(self):
        code, _, err = self.run_cli(str(self.directory / "labels.csv"), "--phone-from", "0.1")
        self.assertEqual(code, 2)
        self.assertIn("cannot go below", err)


if __name__ == "__main__":
    unittest.main()
