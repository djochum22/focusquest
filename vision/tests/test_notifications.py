import unittest
from datetime import timedelta

from focusquest_companion.notifications import TerminalNotifier, WarningPresenter

from support import T0, off, watching


def warned(now, warned_at=T0, seconds_left=60):
    return watching(state="WARNED", server_time=now, warned_at=warned_at,
                    deduction_starts_at=now + timedelta(seconds=seconds_left))


class WarningPresenterTest(unittest.TestCase):

    def setUp(self):
        self.notified = []
        self.presenter = WarningPresenter(TerminalNotifier(self.notified.append))

    def test_warns_once_then_reminds_near_the_end_of_the_grace_period(self):
        self.presenter.show(watching(state="OFF_TASK"), T0)
        self.presenter.show(warned(T0, seconds_left=45), T0)
        self.presenter.show(warned(T0, seconds_left=30), T0)
        self.presenter.show(warned(T0, seconds_left=9), T0)
        self.presenter.show(warned(T0, seconds_left=5), T0)
        self.assertEqual(self.notified, [
            "You seem to be off task. Get back to your work: in 0:45 this time stops counting.",
            "Still off task? This time is about to stop counting.",
        ])

    def test_says_when_the_time_stops_and_starts_counting(self):
        self.presenter.show(warned(T0), T0)
        self.presenter.show(watching(state="DEDUCTING"), T0)
        self.presenter.show(watching(state="DEDUCTING"), T0)
        self.presenter.show(watching(state="ON_TASK"), T0)
        self.assertEqual(self.notified[1:], [
            "Off task: this time isn't counting. It counts again as soon as you are back at your work.",
            "Back on task. Your time counts again.",
        ])

    def test_a_new_warning_is_announced_again(self):
        self.presenter.show(warned(T0, warned_at=T0), T0)
        self.presenter.show(warned(T0, warned_at=T0 + timedelta(minutes=5)), T0)
        self.assertEqual(len(self.notified), 2)

    def test_nothing_is_said_when_the_camera_turns_off(self):
        self.presenter.show(watching(state="DEDUCTING"), T0)
        self.presenter.show(off(), T0)
        self.assertEqual(len(self.notified), 1)


if __name__ == "__main__":
    unittest.main()
