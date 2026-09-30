"""Turns what the detector sees moment by moment into observations: stretches of one signal."""
from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import datetime
from typing import Callable, Iterable

from .api import MAX_OBSERVATIONS_PER_REQUEST, Observation
from .detector import Detection


@dataclass
class _Stretch:
    client_event_id: str
    signal: str
    started_at: datetime
    observed_until: datetime
    confidence: float
    open: bool = True
    #: Changed since the backend last acknowledged it.
    unsent: bool = True

    def snapshot(self) -> Observation:
        return Observation(self.client_event_id, self.signal, self.confidence, self.started_at, self.observed_until)


class ObservationTracker:
    """Keeps a stretch going while its signal is seen, and ends it at the first moment it is not.

    A stretch keeps its id, signal and start, and is sent again with a later end while it lasts. Its
    confidence is the lowest seen during it: a stretch is only as sure as its least sure moment.
    The backend treats a short gap between stretches of one signal as flicker, so none is bridged here.
    """

    def __init__(self, new_id: Callable[[], str] = lambda: uuid.uuid4().hex):
        self._new_id = new_id
        self._stretches: list[_Stretch] = []

    def update(self, detection: Detection, at: datetime, checked: Iterable[str]) -> list[str]:
        """Records one moment. Only the profile's ``checked`` signals count. Returns the signals newly seen."""
        checked = set(checked)
        seen = {signal: confidence for signal, confidence in detection.signals.items() if signal in checked}
        started = []
        for stretch in self._stretches:
            if not stretch.open:
                continue
            if stretch.signal in seen:
                stretch.observed_until = max(stretch.observed_until, at)
                stretch.confidence = min(stretch.confidence, seen.pop(stretch.signal))
                stretch.unsent = True
            else:
                stretch.open = False
        for signal, confidence in seen.items():
            self._stretches.append(_Stretch(self._new_id(), signal, at, at, confidence))
            started.append(signal)
        return started

    def pending(self) -> list[Observation]:
        """What the backend has not heard yet, oldest first, at most one request's worth."""
        return [s.snapshot() for s in self._stretches if s.unsent][:MAX_OBSERVATIONS_PER_REQUEST]

    def acknowledge(self, sent: Iterable[Observation]) -> None:
        """The backend accepted ``sent``. A stretch that went on since the snapshot stays unsent."""
        for observation in sent:
            for stretch in self._stretches:
                if stretch.client_event_id == observation.client_event_id and stretch.snapshot() == observation:
                    stretch.unsent = False
        self._stretches = [s for s in self._stretches if s.open or s.unsent]

    def reset(self) -> None:
        """Forgets everything: the camera closed, or the session changed."""
        self._stretches = []
