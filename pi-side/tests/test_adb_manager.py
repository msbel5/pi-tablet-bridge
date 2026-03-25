import unittest

from adb_manager import parse_adb_devices_output


class ADBManagerTest(unittest.TestCase):
    def test_parse_adb_devices_output(self):
        output = """List of devices attached
HWS10201u0000000000\tdevice
emulator-5554\toffline
"""
        devices = parse_adb_devices_output(output)
        self.assertEqual([{"serial": "HWS10201u0000000000", "state": "device"}], devices)


if __name__ == "__main__":
    unittest.main()
