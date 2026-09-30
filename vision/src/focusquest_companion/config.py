"""Where the pairing is kept: the backend's address and the fqc_ token, readable by this user only."""
from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path


def default_path() -> Path:
    base = os.environ.get("XDG_CONFIG_HOME") or Path.home() / ".config"
    return Path(base) / "focusquest" / "companion.json"


@dataclass(frozen=True)
class Pairing:
    backend_url: str
    token: str


def load(path: Path) -> Pairing | None:
    try:
        data = json.loads(path.read_text())
    except FileNotFoundError:
        return None
    return Pairing(data["backendUrl"], data["token"])


def save(path: Path, pairing: Pairing) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    # Created with owner-only permissions, so the token is never readable by others, even briefly.
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "w") as file:
        json.dump({"backendUrl": pairing.backend_url, "token": pairing.token}, file, indent=2)
    os.chmod(path, 0o600)


def forget(path: Path) -> bool:
    try:
        path.unlink()
        return True
    except FileNotFoundError:
        return False
