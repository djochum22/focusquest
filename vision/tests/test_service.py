import plistlib
import subprocess
import tempfile
import unittest
from pathlib import Path

from focusquest_companion import service
from focusquest_companion.service import LABEL, LaunchAgent


class FakeLaunchctl:
    """Records each launchctl command; answers with the queued results, else success."""

    def __init__(self, **results):
        self.commands = []
        self.results = results

    def __call__(self, command):
        self.commands.append(list(command))
        result = self.results.get(command[1], (0, "", ""))
        # A list answers one call each, then succeeds.
        if isinstance(result, list):
            result = result.pop(0) if result else (0, "", "")
        returncode, stdout, stderr = result
        return subprocess.CompletedProcess(command, returncode, stdout, stderr)


class AgentTest(unittest.TestCase):

    def test_runs_the_camera_in_the_background_with_this_python_at_login_and_again_if_it_stops(self):
        definition = service.agent(Path("/u/.config/focusquest/companion.json"), Path("/u/Library/Logs/c.log"),
                                   python="/venv/bin/python")
        self.assertEqual(definition["ProgramArguments"],
                         ["/usr/bin/sandbox-exec", "-p", service.ONLY_THIS_COMPUTER,
                          "/venv/bin/python", "-m", "focusquest_companion", "--config",
                          "/u/.config/focusquest/companion.json", "run", "--camera", "--background"])
        self.assertTrue(definition["RunAtLoad"])
        self.assertTrue(definition["KeepAlive"])
        self.assertEqual(definition["StandardOutPath"], "/u/Library/Logs/c.log")
        self.assertEqual(definition["StandardErrorPath"], "/u/Library/Logs/c.log")


    def test_the_sandbox_lets_it_connect_only_to_this_computer(self):
        self.assertIn("(deny network-outbound)", service.ONLY_THIS_COMPUTER)
        self.assertIn('(allow network-outbound (remote ip "localhost:*"))', service.ONLY_THIS_COMPUTER)

    def test_any_command_can_run_in_the_sandbox(self):
        self.assertEqual(service.sandboxed(["python", "-m", "x"]),
                         ["/usr/bin/sandbox-exec", "-p", service.ONLY_THIS_COMPUTER, "python", "-m", "x"])

    def test_without_the_sandbox_for_a_backend_elsewhere(self):
        definition = service.agent(Path("/c.json"), Path("/c.log"), python="/venv/bin/python",
                                   only_this_computer=False)
        self.assertEqual(definition["ProgramArguments"][0], "/venv/bin/python")

    def test_knows_which_backends_are_on_this_computer(self):
        for url in ("http://127.0.0.1:8080", "http://localhost:8080", "http://[::1]:8080"):
            self.assertTrue(service.is_this_computer(url), url)
        self.assertFalse(service.is_this_computer("http://192.168.1.20:8080"))


class LaunchAgentTest(unittest.TestCase):

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.path = self.root / "LaunchAgents" / f"{LABEL}.plist"
        self.definition = service.agent(self.root / "companion.json", self.root / "Logs" / "companion.log")

    def test_install_writes_the_agent_and_starts_it_in_place_of_any_earlier_one(self):
        launchctl = FakeLaunchctl()
        LaunchAgent(self.path, launchctl, uid=501).install(self.definition)
        self.assertEqual(plistlib.loads(self.path.read_bytes()), self.definition)
        self.assertTrue((self.root / "Logs").is_dir())
        self.assertEqual(launchctl.commands, [
            ["launchctl", "bootout", f"gui/501/{LABEL}"],
            ["launchctl", "bootstrap", "gui/501", str(self.path)],
        ])

    def test_install_says_why_launchctl_refused(self):
        launchctl = FakeLaunchctl(bootstrap=(5, "", "Bootstrap failed: 5: Input/output error\n"))
        with self.assertRaisesRegex(OSError, "Input/output error"):
            LaunchAgent(self.path, launchctl, uid=501, sleep=lambda _: None).install(self.definition)
        self.assertEqual(len(launchctl.commands), 1 + service.BOOTSTRAP_ATTEMPTS)

    def test_install_tries_again_while_launchd_is_still_stopping_the_earlier_agent(self):
        refused = (5, "", "Bootstrap failed: 5: Input/output error\n")
        launchctl = FakeLaunchctl(bootstrap=[refused, refused])
        waits = []
        LaunchAgent(self.path, launchctl, uid=501, sleep=waits.append).install(self.definition)
        self.assertEqual([c[1] for c in launchctl.commands], ["bootout", "bootstrap", "bootstrap", "bootstrap"])
        self.assertEqual(waits, [1, 1])

    def test_uninstall_stops_and_removes_it(self):
        launchctl = FakeLaunchctl()
        agent = LaunchAgent(self.path, launchctl, uid=501)
        agent.install(self.definition)
        self.assertTrue(agent.uninstall())
        self.assertFalse(self.path.exists())
        self.assertEqual(launchctl.commands[-1], ["launchctl", "bootout", f"gui/501/{LABEL}"])
        self.assertFalse(agent.uninstall())

    def test_status_reads_the_process_from_launchctl(self):
        printed = f"gui/501/{LABEL} = {{\n\tactive count = 1\n\tstate = running\n\tpid = 4242\n}}\n"
        self.assertEqual(LaunchAgent(self.path, FakeLaunchctl(print=(0, printed, "")), uid=501).status(),
                         service.Status(installed=True, running=True, pid=4242))

    def test_status_when_loaded_but_waiting_to_start_again(self):
        printed = f"gui/501/{LABEL} = {{\n\tstate = not running\n}}\n"
        self.assertEqual(LaunchAgent(self.path, FakeLaunchctl(print=(0, printed, "")), uid=501).status(),
                         service.Status(installed=True, running=False))

    def test_status_when_not_loaded(self):
        launchctl = FakeLaunchctl(print=(113, "", "Could not find service"))
        self.assertEqual(LaunchAgent(self.path, launchctl, uid=501).status(),
                         service.Status(installed=False, running=False))


if __name__ == "__main__":
    unittest.main()
