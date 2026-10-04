"""The backend's companion endpoints: the answer they give, and a client for them.

The token works only on /api/companion/** (technical architecture, "Pairing the companion program").
"""
from __future__ import annotations

import json
import re
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any, Callable, Sequence

DEFAULT_BACKEND_URL = "http://127.0.0.1:8080"

#: The backend refuses more than this many observations in one request.
MAX_OBSERVATIONS_PER_REQUEST = 200

_INSTANT = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?Z$")


def parse_instant(text: str | None) -> datetime | None:
    """A Java Instant as JSON: UTC, with up to nine fractional digits, which Python keeps six of."""
    if text is None:
        return None
    match = _INSTANT.match(text)
    if not match:
        raise ValueError(f"Not a UTC instant: {text!r}")
    whole, fraction = match.groups()
    micros = int((fraction or "0")[:6].ljust(6, "0"))
    return datetime.fromisoformat(whole).replace(tzinfo=timezone.utc, microsecond=micros)


def format_instant(moment: datetime) -> str:
    """UTC with milliseconds. Times are stamped to the millisecond, so a stretch's start is sent the same each time."""
    utc = moment.astimezone(timezone.utc)
    return utc.strftime("%Y-%m-%dT%H:%M:%S.") + f"{utc.microsecond // 1000:03d}Z"


@dataclass(frozen=True)
class Check:
    signal: str
    warning_after_seconds: int


@dataclass(frozen=True)
class Profile:
    """What to look for in the session's category: the signals checked, and the work area."""

    category: str
    work_area: str
    checks: tuple[Check, ...]
    grace_seconds: int
    min_confidence: float

    @property
    def signals(self) -> frozenset[str]:
        return frozenset(check.signal for check in self.checks)

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> Profile:
        return cls(
            category=data["category"],
            work_area=data["workArea"],
            checks=tuple(Check(c["signal"], int(c["warningAfterSeconds"])) for c in data["checks"]),
            grace_seconds=int(data["graceSeconds"]),
            min_confidence=float(data["minConfidence"]),
        )


@dataclass(frozen=True)
class CompanionState:
    """The answer to every report. Watch only while ``camera_on``; the other session fields are set only then.

    ``calibrate`` asks the program to calibrate now: the user asked for it in Settings. Never while ``camera_on``.
    """

    camera_on: bool
    session_id: int | None
    profile: Profile | None
    state: str | None
    warned_at: datetime | None
    deduction_starts_at: datetime | None
    server_time: datetime
    calibrate: bool = False

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> CompanionState:
        profile = data.get("profile")
        return cls(
            camera_on=bool(data["cameraOn"]),
            calibrate=bool(data.get("calibrate", False)),
            session_id=data.get("sessionId"),
            profile=Profile.from_json(profile) if profile else None,
            state=data.get("state"),
            warned_at=parse_instant(data.get("warnedAt")),
            deduction_starts_at=parse_instant(data.get("deductionStartsAt")),
            server_time=parse_instant(data["serverTime"]),
        )


@dataclass(frozen=True)
class Observation:
    """One continuous stretch of one signal. Sending the same id again with a later end extends it."""

    client_event_id: str
    signal: str
    confidence: float
    started_at: datetime
    observed_until: datetime

    def to_json(self) -> dict[str, Any]:
        return {
            "clientEventId": self.client_event_id,
            "signal": self.signal,
            "confidence": round(self.confidence, 4),
            "startedAt": format_instant(self.started_at),
            "observedUntil": format_instant(self.observed_until),
        }


class BackendError(Exception):
    """The backend could not be reached, or answered with an error."""


class BackendUnavailable(BackendError):
    """No answer: the backend is not running, or the network failed."""


class Unauthorized(BackendError):
    """The token was refused: the program was unpaired, or paired again elsewhere."""


class Rejected(BackendError):
    """The backend refused the request, for example observations for a session that has just ended."""

    def __init__(self, status: int, message: str):
        super().__init__(f"{status}: {message}")
        self.status = status


class BackendClient:
    def __init__(self, base_url: str, token: str, timeout: float = 10.0,
                 opener: Callable[..., Any] = urllib.request.urlopen):
        self._base_url = base_url.rstrip("/")
        self._token = token
        self._timeout = timeout
        self._opener = opener

    def heartbeat(self) -> CompanionState:
        return CompanionState.from_json(self._post("/api/companion/heartbeat", None))

    def report(self, session_id: int, observations: Sequence[Observation]) -> CompanionState:
        """Sends observations for the session; the backend counts it as a heartbeat too."""
        if len(observations) > MAX_OBSERVATIONS_PER_REQUEST:
            raise ValueError(f"At most {MAX_OBSERVATIONS_PER_REQUEST} observations per request")
        body = {"sessionId": session_id, "observations": [o.to_json() for o in observations]}
        return CompanionState.from_json(self._post("/api/companion/observations", body))

    def calibration(self, outcome: str, calibrated_at: datetime | None = None, message: str | None = None) -> None:
        """Says how calibrating went: ``STARTED``, ``CALIBRATED`` (with when) or ``FAILED`` (with why). Never the angles."""
        body: dict[str, Any] = {"outcome": outcome}
        if calibrated_at is not None:
            body["calibratedAt"] = format_instant(calibrated_at)
        if message is not None:
            body["message"] = message[:300]
        self._post("/api/companion/calibration", body)

    def _post(self, path: str, body: dict[str, Any] | None) -> dict[str, Any]:
        headers = {"Authorization": f"Bearer {self._token}", "Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self._base_url + path, data=data, headers=headers, method="POST")
        try:
            with self._opener(request, timeout=self._timeout) as response:
                content = response.read()
                return json.loads(content) if content else {}
        except urllib.error.HTTPError as error:
            with error:
                if error.code == 401:
                    raise Unauthorized("The pairing code was refused") from error
                raise Rejected(error.code, _error_message(error)) from error
        except (urllib.error.URLError, OSError) as error:
            raise BackendUnavailable(f"Cannot reach the backend at {self._base_url}: {error}") from error


def _error_message(error: urllib.error.HTTPError) -> str:
    try:
        return json.loads(error.read()).get("message") or error.reason
    except (ValueError, AttributeError, OSError):
        return str(error.reason)


class BackendClock:
    """The backend's clock, as best known here.

    Observations may not claim a time more than 5 seconds ahead of the backend's clock, so they are
    stamped in it, not in this computer's: each answer carries ``serverTime``, and the offset is
    taken against the middle of the request.
    """

    def __init__(self, wall: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self._wall = wall
        self._offset = timedelta(0)

    def now(self) -> datetime:
        moment = self._wall() + self._offset
        return moment.replace(microsecond=moment.microsecond // 1000 * 1000)

    def local(self) -> datetime:
        return self._wall()

    def sync(self, server_time: datetime, sent_at: datetime, received_at: datetime) -> None:
        self._offset = server_time - (sent_at + (received_at - sent_at) / 2)
