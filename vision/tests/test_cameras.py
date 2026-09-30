import json
import subprocess
import unittest
from unittest import mock

from focusquest_companion.cameras import CameraChoice, CameraInfo, list_cameras
from focusquest_companion.detector import DetectorUnavailable

MACBOOK = {"_name": "MacBook Air Camera", "spcamera_unique-id": "6C707041-05AC-0011-0003-000000000001"}
IPHONE = {"_name": "Doug's iPhone Camera", "spcamera_unique-id": "1B2F0C9A-0000-0000-0000-000000000002"}


def profiler(*entries):
    def run(command, **kwargs):
        return subprocess.CompletedProcess(command, 0, json.dumps({"SPCameraDataType": list(entries)}), "")
    return run


def on_mac(test):
    return mock.patch("focusquest_companion.cameras.platform.system", return_value="Darwin")(test)


class ListCamerasTest(unittest.TestCase):

    @on_mac
    def test_numbers_cameras_in_the_order_opencv_does_by_unique_id(self, _):
        self.assertEqual(list_cameras(profiler(MACBOOK, IPHONE)), [
            CameraInfo(0, "Doug's iPhone Camera", IPHONE["spcamera_unique-id"]),
            CameraInfo(1, "MacBook Air Camera", MACBOOK["spcamera_unique-id"]),
        ])

    @on_mac
    def test_cannot_list_when_system_profiler_fails(self, _):
        def broken(command, **kwargs):
            raise subprocess.CalledProcessError(1, command)
        self.assertIsNone(list_cameras(broken))

    @mock.patch("focusquest_companion.cameras.platform.system", return_value="Linux")
    def test_cannot_list_outside_macos(self, _):
        self.assertIsNone(list_cameras(profiler(MACBOOK)))


class CameraChoiceTest(unittest.TestCase):
    cameras = [CameraInfo(0, "Doug's iPhone Camera", "1"), CameraInfo(1, "MacBook Air Camera", "6")]

    def test_the_default_is_the_built_in_camera_even_when_an_iphone_comes_first(self):
        self.assertEqual(CameraChoice.parse(None).resolve(self.cameras).name, "MacBook Air Camera")
        self.assertEqual(CameraChoice.parse("built-in").resolve(self.cameras).index, 1)

    def test_a_name_picks_the_camera_whatever_its_number(self):
        self.assertEqual(CameraChoice.parse("iphone").resolve(self.cameras).index, 0)

    def test_a_number_is_used_as_it_is(self):
        self.assertEqual(CameraChoice.parse("0").resolve(self.cameras).name, "Doug's iPhone Camera")
        self.assertEqual(CameraChoice.parse("3").resolve(None), CameraInfo(3, "camera 3", ""))

    def test_without_a_list_the_default_is_the_first_camera(self):
        self.assertEqual(CameraChoice.parse(None).resolve(None).index, 0)

    def test_says_which_cameras_there_are_when_none_matches(self):
        with self.assertRaises(DetectorUnavailable) as caught:
            CameraChoice.parse("logitech").resolve(self.cameras)
        self.assertIn("among: Doug's iPhone Camera, MacBook Air Camera", str(caught.exception))
        with self.assertRaises(DetectorUnavailable):
            CameraChoice.parse(None).resolve([CameraInfo(0, "Doug's iPhone Camera", "1")])
        with self.assertRaises(DetectorUnavailable):
            CameraChoice.parse("iphone").resolve(None)


if __name__ == "__main__":
    unittest.main()
