import json
import threading
import unittest
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from focusquest_companion.api import (BackendClient, BackendClock, BackendUnavailable, Observation, Rejected,
                                      Unauthorized, format_instant, parse_instant)

from support import T0, FakeWall

STATE = {
    "cameraOn": True, "sessionId": 7,
    "profile": {"category": "WRITING", "workArea": "SCREEN_OR_DESK",
                "checks": [{"signal": "AWAY", "warningAfterSeconds": 180},
                           {"signal": "PHONE", "warningAfterSeconds": 20}],
                "graceSeconds": 60, "minConfidence": 0.7},
    "state": "WARNED", "warnedAt": "2026-03-10T09:00:20Z", "deductionStartsAt": "2026-03-10T09:01:20Z",
    "serverTime": "2026-03-10T09:00:30.123456789Z",
}


class FakeBackend(BaseHTTPRequestHandler):
    """Answers like the backend, and remembers what it was sent."""
    requests: list = []
    status = 200
    body: dict = STATE

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        FakeBackend.requests.append((self.path, self.headers.get("Authorization"),
                                     json.loads(self.rfile.read(length)) if length else None))
        payload = b"" if FakeBackend.body is None else json.dumps(FakeBackend.body).encode()
        self.send_response(FakeBackend.status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args):
        pass


class BackendClientTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeBackend)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.url = f"http://127.0.0.1:{cls.server.server_address[1]}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self):
        FakeBackend.requests = []
        FakeBackend.status = 200
        FakeBackend.body = STATE
        self.client = BackendClient(self.url, "fqc_secret")

    def test_heartbeat_sends_the_token_and_reads_the_answer(self):
        state = self.client.heartbeat()
        self.assertEqual(FakeBackend.requests, [("/api/companion/heartbeat", "Bearer fqc_secret", None)])
        self.assertTrue(state.camera_on)
        self.assertEqual(state.session_id, 7)
        self.assertEqual(state.profile.signals, {"AWAY", "PHONE"})
        self.assertEqual(state.profile.min_confidence, 0.7)
        self.assertEqual(state.state, "WARNED")
        self.assertEqual(state.deduction_starts_at, T0 + timedelta(seconds=80))
        self.assertEqual(state.server_time, T0 + timedelta(seconds=30, microseconds=123456))

    def test_an_answer_with_the_camera_off_has_no_session(self):
        FakeBackend.body = {"cameraOn": False, "sessionId": None, "profile": None, "state": None,
                            "warnedAt": None, "deductionStartsAt": None, "serverTime": "2026-03-10T09:00:00Z"}
        state = self.client.heartbeat()
        self.assertFalse(state.camera_on)
        self.assertIsNone(state.profile)

    def test_report_sends_the_observations(self):
        self.client.report(7, [Observation("e1", "PHONE", 0.912345, T0, T0 + timedelta(seconds=1.5))])
        path, _, body = FakeBackend.requests[0]
        self.assertEqual(path, "/api/companion/observations")
        self.assertEqual(body, {"sessionId": 7, "observations": [{
            "clientEventId": "e1", "signal": "PHONE", "confidence": 0.9123,
            "startedAt": "2026-03-10T09:00:00.000Z", "observedUntil": "2026-03-10T09:00:01.500Z"}]})

    def test_an_answer_asking_to_calibrate(self):
        FakeBackend.body = {"cameraOn": False, "calibrate": True, "serverTime": "2026-03-10T09:00:00Z"}
        self.assertTrue(self.client.heartbeat().calibrate)
        FakeBackend.body = STATE
        self.assertFalse(self.client.heartbeat().calibrate)   # a backend that does not say

    def test_calibration_says_how_it_went_and_takes_an_empty_answer(self):
        FakeBackend.status, FakeBackend.body = 204, None
        self.client.calibration("CALIBRATED", T0)
        self.client.calibration("FAILED", message="x" * 400)
        self.assertEqual(FakeBackend.requests[0][0], "/api/companion/calibration")
        self.assertEqual(FakeBackend.requests[0][2], {"outcome": "CALIBRATED", "calibratedAt": "2026-03-10T09:00:00.000Z"})
        self.assertEqual(FakeBackend.requests[1][2], {"outcome": "FAILED", "message": "x" * 300})

    def test_report_refuses_more_than_the_backend_accepts(self):
        with self.assertRaises(ValueError):
            self.client.report(7, [Observation(f"e{i}", "PHONE", 0.9, T0, T0) for i in range(201)])

    def test_a_refused_token_is_unauthorized(self):
        FakeBackend.status = 401
        FakeBackend.body = {}
        with self.assertRaises(Unauthorized):
            self.client.heartbeat()

    def test_other_errors_carry_the_backends_message(self):
        FakeBackend.status = 409
        FakeBackend.body = {"message": "Camera verification is turned off"}
        with self.assertRaises(Rejected) as caught:
            self.client.report(7, [])
        self.assertEqual(caught.exception.status, 409)
        self.assertIn("Camera verification is turned off", str(caught.exception))

    def test_a_backend_that_is_not_running_is_unavailable(self):
        with self.assertRaises(BackendUnavailable):
            BackendClient("http://127.0.0.1:9", "fqc_secret", timeout=2).heartbeat()


class InstantTest(unittest.TestCase):

    def test_parses_what_java_writes(self):
        self.assertEqual(parse_instant("2026-03-10T09:00:00Z"), T0)
        self.assertEqual(parse_instant("2026-03-10T09:00:00.5Z"), T0 + timedelta(milliseconds=500))
        self.assertIsNone(parse_instant(None))
        with self.assertRaises(ValueError):
            parse_instant("2026-03-10T09:00:00+01:00")

    def test_formats_utc_with_milliseconds(self):
        cet = timezone(timedelta(hours=1))
        self.assertEqual(format_instant(datetime(2026, 3, 10, 10, 0, 0, 123999, tzinfo=cet)),
                         "2026-03-10T09:00:00.123Z")


class BackendClockTest(unittest.TestCase):

    def test_follows_the_backends_clock_from_the_middle_of_the_request(self):
        wall = FakeWall(datetime(2030, 1, 1, tzinfo=timezone.utc))
        clock = BackendClock(wall)
        sent = wall()
        wall.advance(0.2)
        clock.sync(T0, sent, wall())
        self.assertEqual(clock.now(), T0 + timedelta(seconds=0.1))
        wall.advance(10)
        self.assertEqual(clock.now(), T0 + timedelta(seconds=10.1))

    def test_stamps_to_the_millisecond(self):
        clock = BackendClock(FakeWall(T0 + timedelta(microseconds=1999)))
        self.assertEqual(clock.now(), T0 + timedelta(milliseconds=1))


if __name__ == "__main__":
    unittest.main()
