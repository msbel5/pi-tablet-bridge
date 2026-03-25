import os
import subprocess
import time


DEFAULT_ADB = "/usr/lib/android-sdk/platform-tools/adb"
PACKAGE_NAME = "com.pitabletbridge"
ACTIVITY_NAME = "com.pitabletbridge/.MainActivity"


class AdbError(RuntimeError):
    pass


class ADBManager(object):
    def __init__(self, adb_path=None, serial=None):
        self.adb_path = adb_path or (DEFAULT_ADB if os.path.exists(DEFAULT_ADB) else "adb")
        self.serial = serial

    def _base_command(self):
        command = [self.adb_path]
        if self.serial:
            command.extend(["-s", self.serial])
        return command

    def _run(self, args, check=True):
        proc = subprocess.run(self._base_command() + list(args), capture_output=True, check=False)
        if check and proc.returncode != 0:
            stderr = proc.stderr.decode("utf-8", errors="replace").strip()
            stdout = proc.stdout.decode("utf-8", errors="replace").strip()
            raise AdbError(stderr or stdout or "adb command failed")
        return proc

    def list_devices(self):
        proc = subprocess.run([self.adb_path, "devices"], capture_output=True, check=False)
        if proc.returncode != 0:
            stderr = proc.stderr.decode("utf-8", errors="replace").strip()
            raise AdbError(stderr or "unable to list adb devices")
        return parse_adb_devices_output(proc.stdout.decode("utf-8", errors="replace"))

    def resolve_serial(self):
        if self.serial:
            return self.serial
        devices = self.list_devices()
        if not devices:
            return None
        return devices[0]["serial"]

    def with_serial(self, serial):
        return ADBManager(self.adb_path, serial=serial)

    def wait_for_device(self, timeout=20.0, poll_interval=1.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            serial = self.resolve_serial()
            if serial:
                return serial
            time.sleep(poll_interval)
        return None

    def package_installed(self, package_name=PACKAGE_NAME):
        proc = self._run(["shell", "pm", "path", package_name], check=False)
        output = proc.stdout.decode("utf-8", errors="replace")
        return proc.returncode == 0 and "package:" in output

    def install_apk(self, apk_path, replace=False):
        if not os.path.exists(apk_path):
            raise AdbError("APK does not exist: %s" % apk_path)
        args = ["install"]
        if replace:
            args.append("-r")
        args.append(apk_path)
        return self._run(args)

    def launch_app(self, activity_name=ACTIVITY_NAME):
        return self._run(["shell", "am", "start", "-n", activity_name])

    def ensure_forward(self, local_port, remote_port):
        self._run(["forward", "--remove", "tcp:%d" % int(local_port)], check=False)
        self._run(["forward", "tcp:%d" % int(local_port), "tcp:%d" % int(remote_port)])


def parse_adb_devices_output(output):
    devices = []
    for line in output.splitlines():
        line = line.strip()
        if not line or line.startswith("List of devices attached"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        devices.append({"serial": parts[0], "state": parts[1]})
    return [device for device in devices if device["state"] == "device"]

