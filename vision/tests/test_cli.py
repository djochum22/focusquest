import contextlib
import io
import stat
import tempfile
import threading
import unittest
from pathlib import Path
from unittest import mock

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


class LabelTest(unittest.TestCase):

    def test_starts_itself_again_in_the_sandbox_with_the_models_downloaded_first(self):
        calls = []
        with mock.patch.object(cli, "_camera_installed", return_value=True), \
                mock.patch.object(cli.service, "supported", return_value=True), \
                mock.patch.dict(cli.os.environ, {}, clear=False), \
                mock.patch.object(cli.models, "ensure", side_effect=lambda model, log: calls.append(model.file_name)), \
                mock.patch.object(cli.os, "execve", side_effect=lambda *a: calls.append(a)):
            cli.os.environ.pop(cli.service.SANDBOXED, None)
            main(["--config", "/c.json", "label", "--category", "coding"])
        *downloaded, (program, command, environment) = calls
        self.assertEqual(len(downloaded), 4)
        self.assertEqual(program, "/usr/bin/sandbox-exec")
        self.assertEqual(command[3:], [cli.sys.executable, "-m", "focusquest_companion",
                                       "--config", "/c.json", "label", "--category", "coding"])
        self.assertEqual(environment[cli.service.SANDBOXED], "1")

    def test_asks_for_a_known_category(self):
        with contextlib.redirect_stderr(io.StringIO()) as err, self.assertRaises(SystemExit):
            main(["label", "--category", "napping"])
        self.assertIn("invalid choice", err.getvalue())


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
