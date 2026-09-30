import hashlib
import io
import tempfile
import unittest
from pathlib import Path

from focusquest_companion.models import Model, ModelError, ensure

CONTENT = b"model weights"
MODEL = Model("face.tflite", "https://models.example/face.tflite", hashlib.sha256(CONTENT).hexdigest())


class Fetch:
    def __init__(self, content=CONTENT, error=None):
        self.content = content
        self.error = error
        self.calls = 0

    def __call__(self, url, timeout):
        self.calls += 1
        if self.error:
            raise self.error
        return io.BytesIO(self.content)


class EnsureTest(unittest.TestCase):

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = Path(directory.name) / "models"

    def test_downloads_once_and_reuses_it(self):
        fetch = Fetch()
        path = ensure(MODEL, self.directory, fetch, log=lambda _: None)
        self.assertEqual(path.read_bytes(), CONTENT)
        self.assertEqual(ensure(MODEL, self.directory, fetch, log=lambda _: None), path)
        self.assertEqual(fetch.calls, 1)
        self.assertEqual([p.name for p in self.directory.iterdir()], ["face.tflite"])

    def test_refuses_a_file_that_is_not_the_pinned_one(self):
        with self.assertRaises(ModelError):
            ensure(MODEL, self.directory, Fetch(b"something else"), log=lambda _: None)
        self.assertFalse((self.directory / "face.tflite").exists())

    def test_replaces_a_damaged_copy(self):
        self.directory.mkdir(parents=True)
        (self.directory / "face.tflite").write_bytes(b"half a file")
        fetch = Fetch()
        self.assertEqual(ensure(MODEL, self.directory, fetch, log=lambda _: None).read_bytes(), CONTENT)
        self.assertEqual(fetch.calls, 1)

    def test_a_failed_download_is_a_model_error(self):
        with self.assertRaises(ModelError):
            ensure(MODEL, self.directory, Fetch(error=OSError("offline")), log=lambda _: None)


if __name__ == "__main__":
    unittest.main()
