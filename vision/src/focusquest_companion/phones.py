"""The phone-in-hand rule: a phone counts only where it overlaps a hand.

The object detector (trained on COCO, whose classes include "cell phone") finds phones lying on a desk
as readily as phones in use, and now and then takes something else for one. A hand around it is what
makes it a phone in hand. Its scores are low even for a phone held up in plain view, so a phone found
in a hand is reported on a rescaled confidence, from the backend's minimum up.

The detector looks at a small copy of the whole frame (320 by 320 pixels), so a phone held further
from the camera, or half out of the picture, is only a few pixels and scores too low. Phones and
hands are therefore also looked for in two enlarged crops of the lower part of the frame, where a
phone in hand usually is (:func:`closer_looks`).
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
#: Each closer look covers this share of the frame's width and height: the left and the right of its
#: lower part, overlapping in the middle so a phone there is whole in one of them.
CLOSER_LOOK_SHARE = 0.6
#: A phone overlapping a surer one by more than this (intersection over union) is the same phone, seen again in a closer look.
SAME_PHONE_FROM = 0.5


@dataclass(frozen=True)
class Found:
    """A phone the object detector found, with its score."""

    score: float
    box: Box


def closer_looks(width: int, height: int) -> list[Box]:
    """The crops of the frame looked at again, enlarged: the lower left and lower right."""
    crop_width, crop_height = int(width * CLOSER_LOOK_SHARE), int(height * CLOSER_LOOK_SHARE)
    top = height - crop_height
    return [(0, top, crop_width, crop_height), (width - crop_width, top, crop_width, crop_height)]


def moved(box: Box, crop: Box) -> Box:
    """A box found in ``crop``, in the whole frame's pixels."""
    return box[0] + crop[0], box[1] + crop[1], box[2], box[3]


def distinct(phones: Sequence[Found]) -> list[Found]:
    """Each phone once, surest first: a closer look finds again what the whole frame found."""
    kept: list[Found] = []
    for phone in sorted(phones, key=lambda phone: phone.score, reverse=True):
        if all(_overlap_share(phone.box, other.box) <= SAME_PHONE_FROM for other in kept):
            kept.append(phone)
    return kept


def _overlap_share(a: Box, b: Box) -> float:
    width = min(a[0] + a[2], b[0] + b[2]) - max(a[0], b[0])
    height = min(a[1] + a[3], b[1] + b[3]) - max(a[1], b[1])
    if width <= 0 or height <= 0:
        return 0.0
    shared = width * height
    return shared / (a[2] * a[3] + b[2] * b[3] - shared)


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
