import itertools
import unittest
from datetime import timedelta

from focusquest_companion.api import Observation
from focusquest_companion.detector import AWAY, LOOKING_AWAY, PHONE, Detection
from focusquest_companion.observations import ObservationTracker

from support import CREATIVE, T0, WRITING


def at(seconds: float):
    return T0 + timedelta(seconds=seconds)


def tracker() -> ObservationTracker:
    ids = itertools.count(1)
    return ObservationTracker(new_id=lambda: f"e{next(ids)}")


class ObservationTrackerTest(unittest.TestCase):

    def test_a_signal_seen_moment_after_moment_is_one_stretch(self):
        t = tracker()
        self.assertEqual(t.update(Detection({PHONE: 0.9}), at(0), WRITING.signals), [PHONE])
        self.assertEqual(t.update(Detection({PHONE: 0.8}), at(1), WRITING.signals), [])
        self.assertEqual(t.update(Detection({PHONE: 0.95}), at(2), WRITING.signals), [])
        self.assertEqual(t.pending(), [Observation("e1", PHONE, 0.8, at(0), at(2))])

    def test_a_stretch_ends_at_the_last_moment_it_was_seen(self):
        t = tracker()
        t.update(Detection({PHONE: 0.9}), at(0), WRITING.signals)
        t.update(Detection({PHONE: 0.9}), at(1), WRITING.signals)
        t.update(Detection(), at(2), WRITING.signals)
        t.update(Detection({PHONE: 0.9}), at(3), WRITING.signals)
        self.assertEqual(t.pending(), [
            Observation("e1", PHONE, 0.9, at(0), at(1)),
            Observation("e2", PHONE, 0.9, at(3), at(3)),
        ])

    def test_signals_are_tracked_separately(self):
        t = tracker()
        t.update(Detection({PHONE: 0.9, LOOKING_AWAY: 0.8}), at(0), WRITING.signals)
        t.update(Detection({LOOKING_AWAY: 0.8}), at(1), WRITING.signals)
        self.assertEqual(t.pending(), [
            Observation("e1", PHONE, 0.9, at(0), at(0)),
            Observation("e2", LOOKING_AWAY, 0.8, at(0), at(1)),
        ])

    def test_only_the_profiles_signals_count(self):
        t = tracker()
        self.assertEqual(t.update(Detection({LOOKING_AWAY: 0.9, AWAY: 0.9}), at(0), CREATIVE.signals), [AWAY])
        self.assertEqual([o.signal for o in t.pending()], [AWAY])

    def test_acknowledged_stretches_are_sent_again_only_once_they_go_on(self):
        t = tracker()
        t.update(Detection({PHONE: 0.9}), at(0), WRITING.signals)
        t.acknowledge(t.pending())
        self.assertEqual(t.pending(), [])

        t.update(Detection({PHONE: 0.9}), at(1), WRITING.signals)
        self.assertEqual(t.pending(), [Observation("e1", PHONE, 0.9, at(0), at(1))])

    def test_a_stretch_that_went_on_while_being_sent_stays_unsent(self):
        t = tracker()
        t.update(Detection({PHONE: 0.9}), at(0), WRITING.signals)
        in_flight = t.pending()
        t.update(Detection({PHONE: 0.9}), at(1), WRITING.signals)
        t.acknowledge(in_flight)
        self.assertEqual(t.pending(), [Observation("e1", PHONE, 0.9, at(0), at(1))])

    def test_ended_and_acknowledged_stretches_are_forgotten(self):
        t = tracker()
        t.update(Detection({PHONE: 0.9}), at(0), WRITING.signals)
        t.update(Detection(), at(1), WRITING.signals)
        t.acknowledge(t.pending())
        t.update(Detection(), at(2), WRITING.signals)
        self.assertEqual(t.pending(), [])
        self.assertEqual(t._stretches, [])

    def test_a_stretch_never_ends_before_it_was_last_seen(self):
        t = tracker()
        t.update(Detection({PHONE: 0.9}), at(5), WRITING.signals)
        t.update(Detection({PHONE: 0.9}), at(4.9), WRITING.signals)   # the clock was corrected backwards
        self.assertEqual(t.pending(), [Observation("e1", PHONE, 0.9, at(5), at(5))])

    def test_sends_at_most_one_requests_worth(self):
        t = tracker()
        for second in range(0, 500, 2):
            t.update(Detection({PHONE: 0.9}), at(second), WRITING.signals)
            t.update(Detection(), at(second + 1), WRITING.signals)
        self.assertEqual(len(t.pending()), 200)
        self.assertEqual(t.pending()[0].client_event_id, "e1")


if __name__ == "__main__":
    unittest.main()
