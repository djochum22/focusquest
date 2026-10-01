"""The detection models, downloaded once and checked against a pinned checksum before use."""
from __future__ import annotations

import hashlib
import os
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable


@dataclass(frozen=True)
class Model:
    file_name: str
    url: str
    sha256: str


#: MediaPipe's BlazeFace, for faces within about 2 metres of the camera.
FACE_DETECTOR = Model(
    "blaze_face_short_range.tflite",
    "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/"
    "blaze_face_short_range.tflite",
    "b4578f35940bf5a1a655214a1cce5cab13eba73c1297cd78e1a04c2380b0152f",
)

#: MediaPipe's Face Landmarker, for which way the head is turned (its facial transformation matrix).
FACE_LANDMARKER = Model(
    "face_landmarker.task",
    "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/"
    "face_landmarker.task",
    "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
)


class ModelError(Exception):
    """A model could not be downloaded, or was not the file expected."""


def cache_dir() -> Path:
    base = os.environ.get("XDG_CACHE_HOME") or Path.home() / ".cache"
    return Path(base) / "focusquest" / "models"


def ensure(model: Model, directory: Path | None = None,
           fetch: Callable[..., Any] = urllib.request.urlopen,
           log: Callable[[str], None] = print) -> Path:
    """The model's local path, downloading it first if it is missing or not the pinned file."""
    directory = directory or cache_dir()
    path = directory / model.file_name
    if path.exists() and _sha256(path.read_bytes()) == model.sha256:
        return path
    log(f"Downloading {model.file_name} (once)...")
    try:
        with fetch(model.url, timeout=60) as response:
            data = response.read()
    except OSError as error:
        raise ModelError(f"Cannot download {model.url}: {error}") from error
    if _sha256(data) != model.sha256:
        raise ModelError(f"{model.url} is not the expected file; not using it")
    directory.mkdir(parents=True, exist_ok=True)
    # Written beside it, then renamed, so a half-written file is never taken for the model.
    with tempfile.NamedTemporaryFile(dir=directory, delete=False) as file:
        file.write(data)
    os.replace(file.name, path)
    return path


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()
