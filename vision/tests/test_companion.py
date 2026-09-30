import threading
import unittest
from datetime import timedelta

from focusquest_companion.api import BackendClock, BackendUnavailable, Rejected, Unauthorized
from focusquest_companion.companion import Companion
from focusquest_companion.detector import PHONE, ScriptedDetector, parse_script
from focusquest_companion.notifications import TerminalNotifier, WarningPresenter

from support import T0, FakeWall, off, watching


class FakeClient:
    """Answers each call with the next queued answer (or raises it), and records what it was sent."""

    def __init__(self, wall: FakeWall):
        self.wall = wall
        self.answers = []
        self.calls = []

    def heartbeat(self):
        self.calls.append(("heartbeat",))
        return self._answer()

    def report(self, session_id, observations):
        self.calls.append(("report", session_id, list(observations)))
        return self._answer()

    def _answer(self):
        answer = self.answers.pop(0)
        if isinstance(answer, Exception):
            raise answer
        # The answer's serverTime is the backend's clock: here, the same as the wall's.
        return answer(self.wall()) if callable(answer) else answer


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


if __name__ == "__main__":
    unittest.main()
