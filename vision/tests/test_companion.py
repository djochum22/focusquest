import threading
import unittest
from datetime import timedelta

from focusquest_companion.api import BackendClock, BackendUnavailable, Rejected, Unauthorized
from focusquest_companion.calibration import DEFAULT, CalibrationFailed
from focusquest_companion.companion import Companion
from focusquest_companion.detector import PHONE, Detection, DetectorUnavailable, ScriptedDetector, parse_script
from focusquest_companion.notifications import TerminalNotifier, WarningPresenter

from support import T0, FakeWall, off, watching


class FakeClient:
    """Answers each call with the next queued answer (or raises it), and records what it was sent."""

    def __init__(self, wall: FakeWall):
        self.wall = wall
        self.answers = []
        self.calls = []
        #: What each heartbeat and report said about the camera.
        self.camera_problems = []

    def heartbeat(self, camera_problem=None):
        self.calls.append(("heartbeat",))
        self.camera_problems.append(camera_problem)
        return self._answer()

    def report(self, session_id, observations, camera_problem=None):
        self.calls.append(("report", session_id, list(observations)))
        self.camera_problems.append(camera_problem)
        return self._answer()

    def calibration(self, outcome, calibrated_at=None, message=None):
        self.calls.append(("calibration", outcome, calibrated_at, message))

    def _answer(self):
        answer = self.answers.pop(0)
        if isinstance(answer, Exception):
            raise answer
        # The answer's serverTime is the backend's clock: here, the same as the wall's.
        return answer(self.wall()) if callable(answer) else answer


class FlakyDetector:
    """Fails to open, or stops seeing, as the test says."""

    def __init__(self):
        self.opens_fail = False
        self.detect_fails = False
        self.opened = 0

    def open(self, now):
        if self.opens_fail:
            raise DetectorUnavailable("Cannot open camera 0.")
        self.opened += 1

    def detect(self, now, profile=None):
        if self.detect_fails:
            raise DetectorUnavailable("The camera stopped sending pictures.")
        return Detection()

    def close(self):
        pass


class FakeCalibrator:
    def __init__(self, calibrated_at=None, fails=None):
        self.saved_at = calibrated_at
        self.fails = fails
        self.calibrated = 0

    def calibrated_at(self):
        return self.saved_at

    def calibrate(self):
        self.calibrated += 1
        if self.fails:
            raise self.fails
        return DEFAULT


class CalibrationTest(unittest.TestCase):

    def companion(self, calibrator):
        self.wall = FakeWall()
        self.client = FakeClient(self.wall)
        self.lines = []
        return Companion(self.client, ScriptedDetector(parse_script("present 1m")),
                         WarningPresenter(TerminalNotifier(self.lines.append)), BackendClock(self.wall),
                         calibrator=calibrator, log=self.lines.append)

    def calibration_calls(self):
        return [call[1:] for call in self.client.calls if call[0] == "calibration"]

    def test_calibrates_when_asked_and_says_so(self):
        calibrator = FakeCalibrator()
        companion = self.companion(calibrator)
        self.client.answers.append(lambda now: off(now, calibrate=True))
        companion.report()
        self.assertEqual(calibrator.calibrated, 1)
        self.assertEqual(self.calibration_calls(), [("STARTED", None, None), ("CALIBRATED", T0, None)])
        self.assertIn("Calibrating, as asked in Settings: follow the steps in the camera window.", self.lines)

    def test_says_why_calibrating_failed(self):
        companion = self.companion(FakeCalibrator(fails=CalibrationFailed("Saw your face 3 times")))
        self.client.answers.append(lambda now: off(now, calibrate=True))
        companion.report()
        self.assertEqual(self.calibration_calls()[-1], ("FAILED", None, "Saw your face 3 times"))

    def test_a_camera_that_cannot_open_fails_the_calibration(self):
        companion = self.companion(FakeCalibrator(fails=DetectorUnavailable("Cannot open camera 0.")))
        self.client.answers.append(lambda now: off(now, calibrate=True))
        companion.report()
        self.assertEqual(self.calibration_calls()[-1], ("FAILED", None, "Cannot open camera 0."))

    def test_without_a_camera_it_says_there_is_nothing_to_calibrate(self):
        companion = self.companion(None)
        self.client.answers.append(lambda now: off(now, calibrate=True))
        companion.report()
        [(outcome, _, message)] = self.calibration_calls()
        self.assertEqual(outcome, "FAILED")
        self.assertIn("replays a script", message)

    def test_says_once_when_the_saved_calibration_was_made(self):
        last_week = T0 - timedelta(days=7)
        companion = self.companion(FakeCalibrator(calibrated_at=last_week))
        self.client.answers += [lambda now: off(now), lambda now: off(now)]
        companion.report()
        companion.report()
        self.assertEqual(self.calibration_calls(), [("CALIBRATED", last_week, None)])

    def test_without_a_saved_calibration_there_is_nothing_to_say(self):
        companion = self.companion(FakeCalibrator())
        self.client.answers.append(lambda now: off(now))
        companion.report()
        self.assertEqual(self.calibration_calls(), [])


class CompanionTest(unittest.TestCase):

    def setUp(self):
        self.wall = FakeWall()
        self.client = FakeClient(self.wall)
        self.detector = ScriptedDetector(parse_script("present 2s, phone 30s"))
        self.lines = []
        self.notified = []
        self.companion = Companion(self.client, self.detector,
                                   WarningPresenter(TerminalNotifier(self.notified.append)),
                                   BackendClock(self.wall), log=self.lines.append)

    def second(self, answer=None):
        """One second passes and the companion reports, with ``answer`` queued for it."""
        if answer is not None:
            self.client.answers.append(answer)
        self.wall.advance(1)
        self.companion.look()
        if answer is not None:
            self.companion.report()

    def test_watches_only_while_the_backend_says_so(self):
        self.second(lambda now: off(now))
        self.assertFalse(self.companion.camera_open)

        self.second(lambda now: watching(server_time=now))
        self.assertTrue(self.companion.camera_open)
        self.assertIn("Camera on for session 1 (WRITING): checking away, looking away, phone.", self.lines)

        self.second(lambda now: off(now))   # paused, ended, or camera verification turned off
        self.assertFalse(self.companion.camera_open)
        self.assertIn("Camera off.", self.lines)

    def test_reports_what_it_sees_as_one_growing_stretch(self):
        self.second(lambda now: watching(server_time=now))     # opened at T0+1: phone from T0+3
        self.second(lambda now: watching(server_time=now))
        self.assertEqual(self.client.calls[-1], ("heartbeat",))

        self.second(lambda now: watching(server_time=now))
        _, session_id, [first] = self.client.calls[-1]
        self.assertEqual((session_id, first.signal, first.started_at, first.observed_until),
                         (1, PHONE, T0 + timedelta(seconds=3), T0 + timedelta(seconds=3)))
        self.assertIn("Seeing: phone.", self.lines)

        self.second(lambda now: watching(server_time=now))
        _, _, [again] = self.client.calls[-1]
        self.assertEqual((again.client_event_id, again.observed_until),
                         (first.client_event_id, T0 + timedelta(seconds=4)))

    def test_stamps_observations_in_the_backends_clock(self):
        self.second(lambda now: watching(server_time=now + timedelta(hours=5)))
        self.second(lambda now: watching(server_time=now + timedelta(hours=5)))
        self.second(lambda now: watching(server_time=now + timedelta(hours=5)))
        _, _, [observation] = self.client.calls[-1]
        self.assertEqual(observation.started_at, T0 + timedelta(hours=5, seconds=3))

    def test_refused_observations_are_dropped_and_the_backend_asked_what_to_do(self):
        self.second(lambda now: watching(server_time=now))
        self.second(lambda now: watching(server_time=now))
        self.client.answers.append(Rejected(409, "Observations are only accepted while the session is running"))
        self.second(lambda now: off(now))
        self.assertEqual([call[0] for call in self.client.calls[-2:]], ["report", "heartbeat"])
        self.assertFalse(self.companion.camera_open)

    def test_closes_the_camera_while_the_backend_cannot_be_reached(self):
        self.second(lambda now: watching(server_time=now))
        self.second(BackendUnavailable("Cannot reach the backend"))
        self.assertFalse(self.companion.camera_open)
        self.second(BackendUnavailable("Cannot reach the backend"))
        self.assertEqual(sum("Cannot reach" in line for line in self.lines), 1)

        self.second(lambda now: watching(server_time=now))
        self.assertTrue(self.companion.camera_open)
        self.assertIn("Reached the backend again.", self.lines)

    def test_a_new_session_starts_afresh(self):
        self.second(lambda now: watching(session_id=1, server_time=now))
        self.second(lambda now: watching(session_id=2, server_time=now))
        self.assertTrue(self.companion.camera_open)
        self.assertIn("Camera on for session 2 (WRITING): checking away, looking away, phone.", self.lines)

    def test_warns_when_the_backend_does(self):
        self.second(lambda now: watching(server_time=now))
        self.second(lambda now: watching(state="WARNED", server_time=now, warned_at=now,
                                         deduction_starts_at=now + timedelta(seconds=60)))
        self.assertIn("Warned: off task. The time stops counting in 60 s.", self.lines)
        self.assertEqual(self.notified, ["You seem to be off task. Get back to your work: in 1:00 this time stops counting."])

    def test_an_unpaired_program_stops_with_the_camera_closed(self):
        self.second(lambda now: watching(server_time=now))
        self.client.answers.append(Unauthorized("The pairing code was refused"))
        with self.assertRaises(Unauthorized):
            self.companion.run(threading.Event(), frame_interval=0)
        self.assertFalse(self.companion.camera_open)


    def test_a_camera_that_cannot_open_is_tried_again_at_each_report(self):
        self.detector = FlakyDetector()
        self.detector.opens_fail = True
        self.companion = Companion(self.client, self.detector,
                                   WarningPresenter(TerminalNotifier(self.notified.append)),
                                   BackendClock(self.wall), log=self.lines.append)
        self.second(lambda now: watching(server_time=now))
        self.second(lambda now: watching(server_time=now))
        self.assertFalse(self.companion.camera_open)
        self.assertEqual(sum("Cannot open camera 0." in line for line in self.lines), 1)

        self.detector.opens_fail = False
        self.second(lambda now: watching(server_time=now))
        self.assertTrue(self.companion.camera_open)
        self.second(lambda now: watching(server_time=now))
        # The first report knew nothing yet; the next two said why; once open, nothing more.
        self.assertEqual(self.client.camera_problems,
                         [None, "Cannot open camera 0.", "Cannot open camera 0.", None])

    def test_a_camera_that_stops_is_closed_and_opened_again(self):
        self.detector = FlakyDetector()
        self.companion = Companion(self.client, self.detector,
                                   WarningPresenter(TerminalNotifier(self.notified.append)),
                                   BackendClock(self.wall), log=self.lines.append)
        self.second(lambda now: watching(server_time=now))
        self.detector.detect_fails = True
        self.second()
        self.assertFalse(self.companion.camera_open)
        self.assertIn("The camera stopped sending pictures. The session is not being checked; trying again.", self.lines)

        self.detector.detect_fails = False
        self.second(lambda now: watching(server_time=now))
        self.assertEqual(self.detector.opened, 2)
        self.assertEqual(self.client.camera_problems[-1], "The camera stopped sending pictures.")
        self.second(lambda now: watching(server_time=now))
        self.assertIsNone(self.client.camera_problems[-1])

    def test_says_nothing_about_a_camera_no_session_wants(self):
        self.detector = FlakyDetector()
        self.detector.opens_fail = True
        self.companion = Companion(self.client, self.detector,
                                   WarningPresenter(TerminalNotifier(self.notified.append)),
                                   BackendClock(self.wall), log=self.lines.append)
        self.second(lambda now: watching(server_time=now))
        self.second(lambda now: off(now))                # the session paused or ended
        self.second(lambda now: off(now))
        self.assertEqual(self.client.camera_problems, [None, "Cannot open camera 0.", None])


if __name__ == "__main__":
    unittest.main()
