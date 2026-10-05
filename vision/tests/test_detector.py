import unittest
from datetime import timedelta

from focusquest_companion.detector import (AWAY, LOOKING_AWAY, PHONE, UNAVAILABLE, Detection, DetectorUnavailable,
                                          ScriptedDetector, Step, parse_script)

from support import T0


def at(seconds: float):
    return T0 + timedelta(seconds=seconds)


class ParseScriptTest(unittest.TestCase):

    def test_reads_steps_with_units_and_confidence(self):
        self.assertEqual(parse_script("present 60s, phone 2m, away 1h @0.8, looking-away 5s, unavailable 30s"), [
            Step(None, timedelta(seconds=60)),
            Step(PHONE, timedelta(minutes=2)),
            Step(AWAY, timedelta(hours=1), 0.8),
            Step(LOOKING_AWAY, timedelta(seconds=5)),
            Step(UNAVAILABLE, timedelta(seconds=30)),
        ])

    def test_refuses_what_it_cannot_read(self):
        for script in ("", "phone", "phone 10", "sleeping 10s", "phone 0s", "phone 10s @1.5"):
            with self.subTest(script=script), self.assertRaises(ValueError):
                parse_script(script)


class ScriptedDetectorTest(unittest.TestCase):

    def test_replays_the_timeline_from_when_it_is_opened(self):
        detector = ScriptedDetector(parse_script("present 60s, phone 90s @0.8"))
        detector.open(T0)
        self.assertEqual(detector.detect(at(0)), Detection())
        self.assertEqual(detector.detect(at(59.9)), Detection())
        self.assertEqual(detector.detect(at(60)), Detection({PHONE: 0.8}))
        self.assertEqual(detector.detect(at(149)), Detection({PHONE: 0.8}))
        self.assertEqual(detector.detect(at(150)), Detection())   # after the script: present
        self.assertEqual(detector.detect(at(10_000)), Detection())

    def test_a_looping_script_starts_over(self):
        detector = ScriptedDetector(parse_script("present 10s, away 5s"), loop=True)
        detector.open(T0)
        self.assertEqual(detector.detect(at(16)), Detection())
        self.assertEqual(detector.detect(at(26)), Detection({AWAY: 0.9}))

    def test_opening_again_starts_the_timeline_again(self):
        detector = ScriptedDetector(parse_script("phone 10s"))
        detector.open(T0)
        detector.close()
        detector.open(at(100))
        self.assertEqual(detector.detect(at(105)), Detection({PHONE: 0.9}))

    def test_an_unavailable_step_fails_until_it_is_over_then_carries_on(self):
        detector = ScriptedDetector(parse_script("present 10s, unavailable 20s, phone 10s"))
        detector.open(T0)
        with self.assertRaises(DetectorUnavailable):
            detector.detect(at(10))
        detector.close()                           # as the companion does, then it tries again
        with self.assertRaises(DetectorUnavailable):
            detector.open(at(20))
        detector.open(at(30))
        self.assertEqual(detector.detect(at(31)), Detection({PHONE: 0.9}))   # the same timeline
        detector.close()
        detector.open(at(100))                     # a normal close starts it again
        self.assertEqual(detector.detect(at(100)), Detection())

    def test_a_closed_detector_sees_nothing(self):
        detector = ScriptedDetector(parse_script("phone 10s"))
        with self.assertRaises(RuntimeError):
            detector.detect(T0)


if __name__ == "__main__":
    unittest.main()
