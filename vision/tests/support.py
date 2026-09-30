"""Shared fakes: a controllable clock, and backend answers."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

from focusquest_companion.api import Check, CompanionState, Profile

T0 = datetime(2026, 3, 10, 9, 0, tzinfo=timezone.utc)

WRITING = Profile("WRITING", "SCREEN_OR_DESK",
                  (Check("AWAY", 180), Check("PHONE", 20), Check("LOOKING_AWAY", 60)), 60, 0.7)
CREATIVE = Profile("CREATIVE", "ANYWHERE", (Check("AWAY", 180), Check("PHONE", 20)), 60, 0.7)


class FakeWall:
    def __init__(self, start: datetime = T0):
        self.now = start

    def __call__(self) -> datetime:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += timedelta(seconds=seconds)


def watching(session_id: int = 1, profile: Profile = WRITING, state: str = "ON_TASK",
             server_time: datetime = T0, warned_at: datetime | None = None,
             deduction_starts_at: datetime | None = None) -> CompanionState:
    return CompanionState(True, session_id, profile, state, warned_at, deduction_starts_at, server_time)


def off(server_time: datetime = T0) -> CompanionState:
    return CompanionState(False, None, None, None, None, None, server_time)
