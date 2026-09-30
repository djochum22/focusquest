import contextlib
import io
import stat
import tempfile
import unittest
from pathlib import Path

from focusquest_companion import config
from focusquest_companion.cli import main


class ConfigTest(unittest.TestCase):

    def test_the_pairing_is_readable_by_this_user_only(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "nested" / "companion.json"
            config.save(path, config.Pairing("http://127.0.0.1:8080", "fqc_secret"))
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertEqual(config.load(path), config.Pairing("http://127.0.0.1:8080", "fqc_secret"))
            self.assertTrue(config.forget(path))
            self.assertIsNone(config.load(path))
            self.assertFalse(config.forget(path))


class CliTest(unittest.TestCase):

    def run_cli(self, *args):
        err = io.StringIO()
        with contextlib.redirect_stderr(err), contextlib.redirect_stdout(io.StringIO()):
            code = main(["--config", str(self.path), *args])
        return code, err.getvalue()

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / "companion.json"

    def test_pairing_needs_a_companion_code(self):
        code, err = self.run_cli("pair", "not-a-code")
        self.assertEqual(code, 2)
        self.assertIn("starts with fqc_", err)
        self.assertFalse(self.path.exists())

    def test_pairing_fails_without_a_backend(self):
        code, err = self.run_cli("pair", "fqc_secret", "--backend", "http://127.0.0.1:9")
        self.assertEqual(code, 1)
        self.assertIn("Cannot reach the backend", err)
        self.assertFalse(self.path.exists())

    def test_running_needs_a_pairing(self):
        code, err = self.run_cli("run", "--script", "phone 10s")
        self.assertEqual(code, 2)
        self.assertIn("Not paired yet", err)

    def test_running_needs_a_readable_script(self):
        config.save(self.path, config.Pairing("http://127.0.0.1:9", "fqc_secret"))
        code, err = self.run_cli("run", "--script", "phone for a while")
        self.assertEqual(code, 2)
        self.assertIn("Cannot read the step", err)


if __name__ == "__main__":
    unittest.main()
