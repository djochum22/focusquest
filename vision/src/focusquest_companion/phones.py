"""The phone-in-hand rule: a phone counts only where it overlaps a hand.

The object detector (trained on COCO, whose classes include "cell phone") finds phones lying on a desk
as readily as phones in use, and now and then takes something else for one. A hand around it is what
makes it a phone in hand. Its scores are low even for a phone held up in plain view, so a phone found
in a hand is reported on a rescaled confidence, from the backend's minimum up.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

#: Where it is in the frame, in pixels: x, y, width, height.
Box = tuple[int, int, int, int]

#: A phone found at least this surely counts, if it overlaps a hand.
PHONE_FROM = 0.25
#: From this score on, a phone in a hand is certain.
CERTAIN_FROM = 0.6
#: The confidence of a phone in a hand found at ``PHONE_FROM``: the backend's minimum (0.7 today).
LEAST_CONFIDENCE = 0.7


@dataclass(frozen=True)
class Found:
    """A phone the object detector found, with its score."""

    score: float
    box: Box


def overlaps(a: Box, b: Box) -> bool:
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def held(phones: Sequence[Found], hands: Sequence[Box]) -> Found | None:
    """The surest phone at or above :data:`PHONE_FROM` that overlaps a hand, if any."""
    in_hand = [phone for phone in phones
               if phone.score >= PHONE_FROM and any(overlaps(phone.box, hand) for hand in hands)]
    return max(in_hand, key=lambda phone: phone.score, default=None)


def confidence(phone: Found) -> float:
    """From :data:`LEAST_CONFIDENCE` at :data:`PHONE_FROM` up to 1 at :data:`CERTAIN_FROM`."""
    share = min(1.0, (phone.score - PHONE_FROM) / (CERTAIN_FROM - PHONE_FROM))
    return round(LEAST_CONFIDENCE + (1 - LEAST_CONFIDENCE) * max(share, 0.0), 3)
