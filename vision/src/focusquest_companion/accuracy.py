"""Accuracy: how often the camera was right, from the files :mod:`.labelling` wrote.

Each recorded second is looked at again from its raw readings (:class:`~.camera.Readings`), with the
thresholds given, so a new threshold can be tried without labelling again. Then two measures:

- **Each second**: how often a signal was reported when the label said so (found), how often the label
  said so when it was reported (right), and how often it was reported while on task.
- **Warnings**: the seconds become stretches as the companion sends them (:mod:`.observations`), and
  the stretches episodes and warnings as the backend works them out. ``OffTaskCalculator.java`` stays
  the source of truth; the copy here only needs to be close enough to estimate.

The user presses a key a moment after changing what they do, so the seconds just after each label
change are left out of the first measure, and a warning that late still counts in the second.
"""
from __future__ import annotations

import csv
import statistics
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path
from typing import Iterable, Sequence

from . import calibration, phones
from .api import Observation, Profile
from .calibration import Calibration, HeadPose
from .camera import LOST_AFTER_TURN, LOST_TURNED_FOR, PRESENT_FROM, away_confidence
from .detector import AWAY, LOOKING_AWAY, PHONE, Detection
from .labelling import (COLUMNS, LABELS, MERGE_GAP_SECONDS, MIN_CONFIDENCE, ON_TASK, WARNING_AFTER_SECONDS,
                        profile_for)
from .observations import ObservationTracker
from .phones import Found

SIGNALS = (AWAY, PHONE, LOOKING_AWAY)
#: How long after a label change the label may still be wrong: the user presses the key a moment late.
LAG = timedelta(seconds=2)
#: Below this much time on task, false warnings an hour are only a rough guess, and the report says so.
ENOUGH_ON_TASK = timedelta(minutes=10)


@dataclass(frozen=True)
class Thresholds:
    """The thresholds to look again with: today's by default."""

    present_from: float = PRESENT_FROM
    margin: float = calibration.MARGIN_DEGREES
    phone_from: float = phones.PHONE_FROM
    certain_from: float = phones.CERTAIN_FROM
    min_confidence: float = MIN_CONFIDENCE
    #: Seconds a face lost after a turn away stays looking away.
    lost_for: float = LOST_TURNED_FOR.total_seconds()

    def problem(self) -> str | None:
        """Why these cannot be tried on a recorded file, or None if they can."""
        if self.present_from < PRESENT_FROM:
            return (f"--present-from cannot go below {PRESENT_FROM}: the head's angles were not recorded "
                    "for fainter faces.")
        if self.present_from > 1:
            return "--present-from is a score from 0 to 1."
        if self.phone_from < phones.PHONE_FROM:
            return (f"--phone-from cannot go below {phones.PHONE_FROM}: hands were not looked for "
                    "below it.")
        if self.certain_from <= self.phone_from:
            return "--certain-from must be above --phone-from."
        if not 0 < self.min_confidence <= 1:
            return "--min-confidence is between 0 and 1."
        if self.margin < 0:
            return "--margin cannot be negative."
        if self.lost_for < 0:
            return "--lost-for cannot be negative."
        return None

    def describe(self) -> str:
        return (f"present from {self.present_from:g}, looking away from {self.margin:g} degrees beyond the zone, "
                f"phone from {self.phone_from:g} (certain from {self.certain_from:g}), "
                f"a lost face looking away for up to {self.lost_for:g} seconds after a turn, "
                f"minimum confidence {self.min_confidence:g}")


@dataclass(frozen=True)
class Row:
    """One recorded second."""

    time: datetime
    category: str
    label: str
    #: What was reported while labelling, with confidence.
    reported: dict[str, float]
    face_score: float
    yaw: float | None
    pitch: float | None
    held_phone_score: float | None


def read(path: Path) -> list[Row]:
    """The rows of a file :mod:`.labelling` wrote. Raises ValueError for anything else."""
    with path.open(newline="") as file:
        reader = csv.DictReader(file)
        if tuple(reader.fieldnames or ()) != COLUMNS:
            raise ValueError(f"{path} is not a file written by `label`.")
        rows = []
        for line in reader:
            if line["label"] not in LABELS:
                raise ValueError(f"{path}: unknown label {line['label']!r}.")
            rows.append(Row(
                time=datetime.fromisoformat(line["time"]), category=line["category"], label=line["label"],
                reported={signal: float(line[column]) for signal, column
                          in ((AWAY, "away"), (PHONE, "phone"), (LOOKING_AWAY, "looking_away")) if line[column]},
                face_score=float(line["face_score"]), yaw=_maybe(line["yaw"]), pitch=_maybe(line["pitch"]),
                held_phone_score=_maybe(line["held_phone_score"])))
    return rows


def _maybe(cell: str) -> float | None:
    return float(cell) if cell else None


def replay(rows: Sequence[Row], profile: Profile, angles: Calibration, thresholds: Thresholds) -> list[dict[str, float]]:
    """What would have been reported each second with these thresholds: the rules of
    :meth:`.camera.CameraDetector.detect`, from the recorded readings instead of a frame."""
    checks_looking = LOOKING_AWAY in profile.signals
    turned: tuple[datetime, float] | None = None
    lost_turned: tuple[datetime, float] | None = None
    lost_for = timedelta(seconds=thresholds.lost_for)
    replayed = []
    for row in rows:
        signals: dict[str, float] = {}
        if row.face_score >= thresholds.present_from:
            lost_turned = looking_away = None
            if checks_looking and row.yaw is not None and row.pitch is not None:
                looking_away = angles.looking_away(HeadPose(row.yaw, row.pitch), profile.work_area,
                                                   row.face_score, thresholds.margin)
            turned = (row.time, looking_away) if looking_away is not None else None
            if looking_away is not None:
                signals[LOOKING_AWAY] = looking_away
        else:
            if checks_looking and turned is not None and row.time - turned[0] <= LOST_AFTER_TURN:
                lost_turned = (row.time, turned[1])
            turned = None
            if lost_turned is not None and row.time - lost_turned[0] > lost_for:
                lost_turned = None
            if checks_looking and lost_turned is not None:
                signals[LOOKING_AWAY] = lost_turned[1]
            else:
                signals[AWAY] = away_confidence(row.face_score, thresholds.present_from)
        if PHONE in profile.signals and row.held_phone_score is not None \
                and row.held_phone_score >= thresholds.phone_from:
            signals[PHONE] = phones.confidence(Found(row.held_phone_score, (0, 0, 0, 0)),
                                               thresholds.phone_from, thresholds.certain_from)
        replayed.append(signals)
    return replayed


def differences(rows: Sequence[Row], replayed: Sequence[dict[str, float]]) -> int:
    """How many seconds a different signal was found in than was reported while labelling. Only which
    signals, not how surely: a confidence rule changed since recording is no reason to doubt the file."""
    return sum(row.reported.keys() != signals.keys() for row, signals in zip(rows, replayed))


@dataclass(frozen=True)
class Episode:
    """As ``OffTaskCalculator.Episode``, with the signal that warned. ``warned_at`` None if none did."""

    started_at: datetime
    ended_at: datetime
    warned_at: datetime | None
    warned_for: str | None


def observations(rows: Sequence[Row], replayed: Sequence[dict[str, float]], profile: Profile) -> list[Observation]:
    """The stretches the companion would have sent: as many as there were, each as it was last sent."""
    tracker = ObservationTracker()
    sent: dict[str, Observation] = {}
    for row, signals in zip(rows, replayed):
        tracker.update(Detection(signals), row.time, profile.signals)
        while batch := tracker.pending():
            sent.update((observation.client_event_id, observation) for observation in batch)
            tracker.acknowledge(batch)
    return list(sent.values())


def episodes(observed: Iterable[Observation], profile: Profile, min_confidence: float,
             merge_gap: timedelta, horizon: datetime) -> list[Episode]:
    """``OffTaskCalculator.episodes``: stretches below the minimum dropped, a signal's stretches at most
    ``merge_gap`` apart joined, then any signal's into episodes, warned when one signal has lasted its
    warning time."""
    warning_after = {check.signal: timedelta(seconds=check.warning_after_seconds) for check in profile.checks}
    by_signal: dict[str, list[tuple[datetime, datetime]]] = {}
    for observation in sorted(observed, key=lambda o: o.started_at):
        end = min(observation.observed_until, horizon)
        if observation.signal in warning_after and observation.confidence >= min_confidence \
                and end > observation.started_at:
            by_signal.setdefault(observation.signal, []).append((observation.started_at, end))
    stretches = sorted(((start, end, signal) for signal, found in by_signal.items()
                        for start, end in _merged(found, merge_gap)), key=lambda stretch: stretch[0])

    result: list[Episode] = []
    current: list[tuple[datetime, datetime, str]] = []
    for stretch in stretches:
        if current and stretch[0] > max(end for _, end, _ in current) + merge_gap:
            result.append(_episode(current, warning_after))
            current = []
        current.append(stretch)
    if current:
        result.append(_episode(current, warning_after))
    return result


def _merged(stretches: list[tuple[datetime, datetime]], gap: timedelta) -> list[tuple[datetime, datetime]]:
    merged: list[tuple[datetime, datetime]] = []
    for start, end in stretches:
        if merged and start <= merged[-1][1] + gap:
            merged[-1] = (merged[-1][0], max(end, merged[-1][1]))
        else:
            merged.append((start, end))
    return merged


def _episode(stretches: list[tuple[datetime, datetime, str]], warning_after: dict[str, timedelta]) -> Episode:
    warnings = [(start + warning_after[signal], signal) for start, end, signal in stretches
                if start + warning_after[signal] <= end]
    warned_at, warned_for = min(warnings, default=(None, None))
    return Episode(stretches[0][0], max(end for _, end, _ in stretches), warned_at, warned_for)


@dataclass
class SecondCounts:
    """One signal, counted second by second, in files whose category checks it."""

    labelled: int = 0
    #: Labelled so, and reported.
    found: int = 0
    reported: int = 0
    on_task: int = 0
    #: Reported while on task.
    false: int = 0


@dataclass
class WarningCounts:
    """One signal's warnings."""

    #: Labelled stretches long enough to be warned about in time, and how many were.
    long_enough: int = 0
    warned: int = 0
    #: Of those not warned about for this signal, how many were for another.
    warned_otherwise: int = 0
    shorter: int = 0
    #: From the labelled stretch's start to the warning.
    delays: list[float] = field(default_factory=list)
    #: Seconds in the stretches the companion would have sent, and in those less sure than the minimum,
    #: which the backend ignores.
    sent: float = 0.0
    too_unsure: float = 0.0
    #: Warnings for this signal while on task.
    false: int = 0
    on_task: timedelta = timedelta()


@dataclass
class Report:
    thresholds: Thresholds
    files: int = 0
    seconds: dict[str, int] = field(default_factory=lambda: {label: 0 for label in LABELS})
    left_out: int = 0
    #: Seconds replayed differently from what was reported while labelling, with today's thresholds.
    differing: int = 0
    per_second: dict[str, SecondCounts] = field(default_factory=lambda: {s: SecondCounts() for s in SIGNALS})
    warnings: dict[str, WarningCounts] = field(default_factory=lambda: {s: WarningCounts() for s in SIGNALS})
    on_task: timedelta = timedelta()

    def add(self, rows: Sequence[Row], angles: Calibration) -> None:
        """One file: one run of ``label``, in one category."""
        if not rows:
            return
        self.files += 1
        profile = profile_for(rows[0].category)
        replayed = replay(rows, profile, angles, self.thresholds)
        self.differing += differences(rows, replay(rows, profile, angles, Thresholds()))
        for row in rows:
            self.seconds[row.label] += 1
        self.on_task += timedelta(seconds=self.seconds_on_task(rows))
        self._count_seconds(rows, replayed, profile)
        self._count_warnings(rows, replayed, profile)

    @staticmethod
    def seconds_on_task(rows: Sequence[Row]) -> int:
        return sum(row.label == ON_TASK for row in rows)

    def _count_seconds(self, rows: Sequence[Row], replayed: Sequence[dict[str, float]], profile: Profile) -> None:
        changed_at = rows[0].time - LAG
        for index, (row, signals) in enumerate(zip(rows, replayed)):
            if index > 0 and row.label != rows[index - 1].label:
                changed_at = row.time
            if row.time - changed_at < LAG:
                self.left_out += 1
                continue
            for signal in profile.signals:
                counts = self.per_second[signal]
                reported = signals.get(signal, 0.0) >= self.thresholds.min_confidence
                counts.reported += reported
                if row.label == signal:
                    counts.labelled += 1
                    counts.found += reported
                elif row.label == ON_TASK:
                    counts.on_task += 1
                    counts.false += reported

    def _count_warnings(self, rows: Sequence[Row], replayed: Sequence[dict[str, float]], profile: Profile) -> None:
        horizon = rows[-1].time + timedelta(seconds=1)
        sent = observations(rows, replayed, profile)
        found = episodes(sent, profile, self.thresholds.min_confidence, timedelta(seconds=MERGE_GAP_SECONDS), horizon)
        warned = [(episode.warned_at, episode.warned_for) for episode in found if episode.warned_at is not None]
        warning_after = {check.signal: check.warning_after_seconds for check in profile.checks}
        for observation in sent:
            # A stretch seen in n looks lasts n - 1 seconds by its times; counted here as the n seconds it covers.
            seconds = (observation.observed_until - observation.started_at).total_seconds() + 1
            self.warnings[observation.signal].sent += seconds
            if observation.confidence < self.thresholds.min_confidence:
                self.warnings[observation.signal].too_unsure += seconds
        on_task = timedelta(seconds=self.seconds_on_task(rows))
        for signal in profile.signals:
            self.warnings[signal].on_task += on_task

        for label, start, end in _stretches(rows):
            if label not in profile.signals:
                continue
            counts = self.warnings[label]
            if end - start < timedelta(seconds=warning_after[label]):
                counts.shorter += 1
                continue
            counts.long_enough += 1
            during = [(at, signal) for at, signal in warned if start <= at <= end + LAG]
            mine = [at for at, signal in during if signal == label]
            if mine:
                counts.warned += 1
                counts.delays.append((mine[0] - start).total_seconds())
            elif during:
                counts.warned_otherwise += 1

        for at, signal in warned:
            if all(row.label == ON_TASK for row in rows if at - LAG <= row.time <= at) \
                    and _label_at(rows, at) == ON_TASK:
                self.warnings[signal].false += 1


def _stretches(rows: Sequence[Row]) -> list[tuple[str, datetime, datetime]]:
    """Each run of one label: the label, when it started and when it ended (a second after its last row)."""
    runs: list[tuple[str, datetime, datetime]] = []
    for row in rows:
        if runs and runs[-1][0] == row.label:
            runs[-1] = (row.label, runs[-1][1], row.time + timedelta(seconds=1))
        else:
            runs.append((row.label, row.time, row.time + timedelta(seconds=1)))
    return runs


def _label_at(rows: Sequence[Row], moment: datetime) -> str:
    """The label given at ``moment``: that of the last row at or before it."""
    label = rows[0].label
    for row in rows:
        if row.time > moment:
            break
        label = row.label
    return label


def measure(paths: Sequence[Path], angles: Calibration, thresholds: Thresholds = Thresholds()) -> Report:
    report = Report(thresholds)
    for path in paths:
        report.add(read(path), angles)
    return report


NAMES = {AWAY: "Away", PHONE: "Phone", LOOKING_AWAY: "Looking away", ON_TASK: "On task"}


def format_report(report: Report) -> list[str]:
    """The report, as lines for the terminal."""
    total = sum(report.seconds.values())
    lines = [f"{report.files} file{'s' if report.files != 1 else ''}, {total} seconds labelled: "
             + ", ".join(f"{report.seconds[label]} {NAMES[label].lower()}" for label in LABELS) + ".",
             f"Thresholds: {report.thresholds.describe()}."]
    if report.differing:
        lines.append(f"Note: {report.differing} of {total} seconds come out differently from what was reported "
                     "while labelling, with today's thresholds: the companion's rules or the calibration have changed "
                     "since. Looking away is judged against the current calibration.")

    lines += ["", f"Each second (leaving out the {int(LAG.total_seconds())} seconds after each label change, "
                  f"{report.left_out} in all)",
              f"{'':<14}{'Found when so':<22}{'Right when reported':<24}False while on task"]
    for signal in SIGNALS:
        counts = report.per_second[signal]
        minutes = counts.on_task / 60
        false = f"{counts.false / minutes:.1f} a minute ({counts.false} of {counts.on_task} s)" if minutes else "-"
        lines.append(f"{NAMES[signal]:<14}{_share(counts.found, counts.labelled):<22}"
                     f"{_share(counts.found, counts.reported):<24}{false}")

    lines += ["", f"Warnings (gaps of up to {MERGE_GAP_SECONDS} seconds merged; a warning up to "
                  f"{int(LAG.total_seconds())} seconds after the label changed back still counts)",
              f"{'':<22}{'Warned in time':<18}{'Median delay':<15}False warnings"]
    for signal in SIGNALS:
        counts = report.warnings[signal]
        warned = f"{counts.warned} of {counts.long_enough}" if counts.long_enough else "-"
        delay = f"{statistics.median(counts.delays):.0f} s" if counts.delays else "-"
        hours = counts.on_task.total_seconds() / 3600
        false = f"{counts.false} (about {counts.false / hours:.0f} an hour)" if hours else "-"
        name = f"{NAMES[signal]} ({WARNING_AFTER_SECONDS[signal]} s)"
        lines.append(f"{name:<22}{warned:<18}{delay:<15}{false}")
    notes = []
    for signal in SIGNALS:
        counts = report.warnings[signal]
        if counts.warned_otherwise:
            notes.append(f"{counts.warned_otherwise} {NAMES[signal].lower()} stretch"
                         f"{'es were' if counts.warned_otherwise != 1 else ' was'} warned about for another signal.")
        if counts.too_unsure:
            notes.append(f"{NAMES[signal]}: {counts.too_unsure:.0f} of the {counts.sent:.0f} seconds sent were in "
                         "stretches less sure than the minimum, which the backend ignores. A stretch is only as "
                         "sure as its least sure second.")
        if counts.shorter:
            notes.append(f"{counts.shorter} {NAMES[signal].lower()} stretch"
                         f"{'es were' if counts.shorter != 1 else ' was'} shorter than the warning time, "
                         "so left out of \"warned in time\".")
    if report.on_task < ENOUGH_ON_TASK:
        notes.append(f"Only {report.on_task.total_seconds() / 60:.1f} minutes on task: false alarms a minute or "
                     f"an hour are rough until there are at least {ENOUGH_ON_TASK.total_seconds() / 60:.0f}.")
    return lines + ([""] + notes if notes else [])


def _share(part: int, whole: int) -> str:
    return f"{part} of {whole} ({part / whole:.0%})" if whole else "-"


def default_paths(folder: Path) -> list[Path]:
    """Every file ``label`` wrote to ``folder``, oldest first."""
    return sorted(folder.glob("labels-*.csv"))
