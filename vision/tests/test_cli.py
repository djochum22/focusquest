import contextlib
import io
import stat
import tempfile
import threading
import unittest
from pathlib import Path

from focusquest_companion import cli, config
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


class AwaitPairingTest(unittest.TestCase):

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / "companion.json"
        self.lines = []

    def test_in_the_background_it_waits_until_paired(self):
        pairing = config.Pairing("http://127.0.0.1:8080", "fqc_new")

        class PairedWhileWaiting(threading.Event):
            def wait(event, timeout=None):
                config.save(self.path, pairing)
                return False

        self.assertEqual(cli._await_pairing(self.path, None, True, PairedWhileWaiting(), self.lines.append), pairing)
        self.assertIn("Waiting to be paired", self.lines[0])

    def test_a_refused_pairing_is_waited_past(self):
        refused = config.Pairing("http://127.0.0.1:8080", "fqc_old")
        config.save(self.path, refused)
        stop = threading.Event()
        stop.set()
        self.assertIsNone(cli._await_pairing(self.path, refused, True, stop, self.lines.append))
        self.assertEqual(len(self.lines), 1)

    def test_in_a_terminal_it_does_not_wait(self):
        with contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertIsNone(cli._await_pairing(self.path, None, False, threading.Event(), self.lines.append))
        self.assertIn("Not paired yet", err.getvalue())


if __name__ == "__main__":
    unittest.main()
