"""The companion's loop: look, report, and do what the backend's answer says."""
from __future__ import annotations

import threading
import time
from datetime import datetime
from typing import Callable

from .api import BackendClient, BackendClock, BackendUnavailable, CompanionState, Rejected
from .detector import Detector, DetectorUnavailable
from .notifications import WarningPresenter, seconds_left
from .observations import ObservationTracker

#: How often the detector looks.
FRAME_INTERVAL_SECONDS = 1.0
#: How often the backend hears from the program. It counts as connected if heard from within 30 s.
REPORT_INTERVAL_SECONDS = 2.0


class Companion:
    """Watches only while the backend's last answer said ``cameraOn``, and closes the camera as soon as
    it does not, or as soon as the backend cannot be reached: nothing is watched that nobody asked for.

    A camera that cannot be opened, or stops sending pictures, is tried again at each report. Until
    then nothing is observed, so nothing can be subtracted.
    """

    def __init__(self, client: BackendClient, detector: Detector, presenter: WarningPresenter,
                 clock: BackendClock, *, tracker: ObservationTracker | None = None,
                 log: Callable[[str], None] = print, monotonic: Callable[[], float] = time.monotonic):
        self._client = client
        self._detector = detector
        self._presenter = presenter
        self._clock = clock
        self._tracker = tracker or ObservationTracker()
        self._log = log
        self._monotonic = monotonic
        self._state: CompanionState | None = None
        self._session_id: int | None = None
        self._camera_open = False
        self._reachable = True
        self._next_report = 0.0
        #: Why the detector cannot see, while it cannot; said once, not at every retry.
        self._unavailable: str | None = None
        #: The state last written to the terminal, "OFF" while nothing is watched.
        self._described: str | None = None

    @property
    def camera_open(self) -> bool:
        return self._camera_open

    def run(self, stop: threading.Event, frame_interval: float = FRAME_INTERVAL_SECONDS,
            report_interval: float = REPORT_INTERVAL_SECONDS) -> None:
        """Runs until ``stop`` is set. :class:`~.api.Unauthorized` ends it: the program needs pairing again."""
        try:
            while not stop.is_set():
                self.tick(report_interval)
                stop.wait(frame_interval)
        finally:
            self._close_camera()

    def tick(self, report_interval: float = REPORT_INTERVAL_SECONDS) -> None:
        """Looks once, and reports if it is time to."""
        self.look()
        if self._monotonic() >= self._next_report:
            self.report()
            self._next_report = self._monotonic() + report_interval

    def look(self) -> None:
        if not self._camera_open or self._state is None or self._state.profile is None:
            return
        now = self._clock.now()
        try:
            detection = self._detector.detect(now, self._state.profile)
        except DetectorUnavailable as error:
            self._say_unavailable(error)
            self._close_camera()
            return
        for signal in self._tracker.update(detection, now, self._state.profile.signals):
            self._log(f"Seeing: {_name(signal)}.")

    def report(self) -> None:
        """Sends what the backend has not heard yet, or a heartbeat, and follows the answer."""
        sent_at = self._clock.local()
        try:
            state = self._exchange()
        except BackendUnavailable as error:
            if self._reachable:
                self._log(f"{error}. Trying again.")
            self._reachable = False
            self._close_camera()
            return
        if not self._reachable:
            self._log("Reached the backend again.")
        self._reachable = True
        self._clock.sync(state.server_time, sent_at, self._clock.local())
        self._follow(state)

    def _exchange(self) -> CompanionState:
        pending = self._tracker.pending() if self._camera_open else []
        if pending and self._session_id is not None:
            try:
                state = self._client.report(self._session_id, pending)
            except Rejected as error:
                # The session ended or paused in the meantime; what was seen is no longer wanted.
                self._log(f"The backend refused the observations ({error}); leaving them out.")
                self._tracker.reset()
            else:
                self._tracker.acknowledge(pending)
                signals = ", ".join(sorted({_name(o.signal) for o in pending}))
                self._log(f"Reported {len(pending)} observation{'s' if len(pending) != 1 else ''}: {signals}.")
                return state
        return self._client.heartbeat()

    def _follow(self, state: CompanionState) -> None:
        watch = state.camera_on and state.profile is not None and state.session_id is not None
        if self._camera_open and (not watch or state.session_id != self._session_id):
            self._close_camera()
        self._state = state
        if watch and not self._camera_open:
            try:
                self._detector.open(self._clock.now())
            except DetectorUnavailable as error:
                self._say_unavailable(error)
            else:
                self._unavailable = None
                self._camera_open = True
                self._session_id = state.session_id
                checks = ", ".join(_name(signal) for signal in sorted(state.profile.signals))
                self._log(f"Camera on for session {state.session_id} ({state.profile.category}): checking {checks}.")
        described = state.state if state.camera_on else "OFF"
        if described != self._described:
            self._log(_describe(state, self._clock.now()))
            self._described = described
        self._presenter.show(state, self._clock.now())

    def _say_unavailable(self, error: DetectorUnavailable) -> None:
        if str(error) != self._unavailable:
            self._log(f"{error} The session is not being checked; trying again.")
        self._unavailable = str(error)

    def _close_camera(self) -> None:
        if not self._camera_open:
            return
        self._detector.close()
        self._tracker.reset()
        self._camera_open = False
        self._session_id = None
        self._log("Camera off.")


def _name(signal: str) -> str:
    return signal.lower().replace("_", " ")


def _describe(state: CompanionState, now: datetime) -> str:
    if not state.camera_on:
        return "No camera-verified session is running."
    if state.state == "WARNED":
        return f"Warned: off task. The time stops counting in {seconds_left(state, now)} s."
    if state.state == "DEDUCTING":
        return "Off task: the time is not counting."
    return f"Session state: {state.state}."
