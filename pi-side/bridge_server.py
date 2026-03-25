#!/usr/bin/env python3
import argparse
import os
import sys
import threading
import time

from adb_manager import ACTIVITY_NAME, PACKAGE_NAME, ADBManager, AdbError
from protocol import (
    MSG_CONFIG,
    MSG_FRAME,
    MSG_HELLO,
    MSG_KEY,
    MSG_PING,
    MSG_PONG,
    MSG_TOUCH,
    PROTOCOL_VERSION,
    build_frame_payload,
    decode_json,
)
from touch import TouchDispatcher
from transport import TransportManager


APP_VERSION = "0.1.0"
DEFAULT_PORT = 19876
DEFAULT_DISCOVERY_PORT = 19877
DEFAULT_FPS = 6
DEFAULT_JPEG_QUALITY = 50
DEFAULT_SCALE = 0.5


class BridgeServer(object):
    def __init__(self, apk_path=None, adb_path=None, port=DEFAULT_PORT, discovery_port=DEFAULT_DISCOVERY_PORT):
        self.apk_path = apk_path
        self.port = port
        self.discovery_port = discovery_port
        self.adb = ADBManager(adb_path)
        self.touch = TouchDispatcher()
        self.capture_worker = None
        self.transport = TransportManager(
            adb_manager=self.adb,
            port=port,
            discovery_port=discovery_port,
            on_message=self._handle_message,
            on_session_ready=self._handle_session_ready,
            on_session_closed=self._handle_session_closed,
        )
        self.stop_event = threading.Event()
        self.sender_thread = threading.Thread(target=self._frame_sender_loop, name="frame-sender")
        self.sender_thread.daemon = True
        self._config_signature = None

    def install_and_launch(self):
        serial = self.adb.wait_for_device(timeout=5.0, poll_interval=0.5)
        if not serial:
            raise AdbError("no adb device connected")
        device_adb = self.adb.with_serial(serial)
        if self.apk_path and not device_adb.package_installed(PACKAGE_NAME):
            device_adb.install_apk(self.apk_path)
        device_adb.launch_app(ACTIVITY_NAME)

    def start(self):
        self._ensure_capture_worker()
        self.capture_worker.start()
        self.transport.start()
        self.sender_thread.start()

    def stop(self):
        self.stop_event.set()
        self.transport.stop()
        self.capture_worker.stop()
        self.touch.reset()
        if self.sender_thread.is_alive():
            self.sender_thread.join(timeout=2.0)

    def _hello_payload(self, transport_name):
        return {
            "app_version": APP_VERSION,
            "capabilities": ["display", "touch", "keyboard"],
            "protocol_version": PROTOCOL_VERSION,
            "role": "pi",
            "transport": transport_name,
        }

    def _config_payload(self, frame):
        return {
            "fps": DEFAULT_FPS,
            "frame_scale": DEFAULT_SCALE,
            "jpeg_quality": DEFAULT_JPEG_QUALITY,
            "screen_height": frame.screen_height,
            "screen_width": frame.screen_width,
        }

    def _handle_session_ready(self, session):
        session.send_json(MSG_HELLO, self._hello_payload(session.transport_name))
        if not self.capture_worker:
            return
        latest_version, frame = self.capture_worker.frame_store.get_if_newer(0)
        if frame:
            self._send_config_if_changed(session, frame)

    def _handle_session_closed(self, session):
        if session.transport_name == "usb":
            self.touch.reset()

    def _send_config_if_changed(self, session, frame):
        config = self._config_payload(frame)
        signature = tuple(sorted(config.items()))
        if signature != self._config_signature:
            self.touch.update_screen_size(frame.screen_width, frame.screen_height)
            session.send_json(MSG_CONFIG, config)
            self._config_signature = signature

    def _frame_sender_loop(self):
        seen_version = 0
        while not self.stop_event.is_set():
            if not self.capture_worker:
                self.stop_event.wait(0.1)
                continue
            seen_version, frame = self.capture_worker.frame_store.get_if_newer(seen_version)
            if frame is None:
                self.stop_event.wait(0.05)
                continue
            session = self.transport.get_active_session()
            if not session or session.is_closed():
                self.stop_event.wait(0.05)
                continue
            self._send_config_if_changed(session, frame)
            payload = build_frame_payload(
                frame_id=frame.frame_id,
                width=frame.width,
                height=frame.height,
                timestamp_ms=frame.timestamp_ms,
                jpeg_bytes=frame.jpeg_bytes,
            )
            session.send_payload(MSG_FRAME, payload)

    def _handle_message(self, session, message_type, payload):
        if message_type == MSG_TOUCH:
            self.touch.handle_touch_event(decode_json(payload))
        elif message_type == MSG_KEY:
            self.touch.handle_key_event(decode_json(payload))
        elif message_type in (MSG_HELLO, MSG_CONFIG, MSG_PONG, MSG_PING):
            return

    def status(self):
        devices = self.adb.list_devices()
        active = self.transport.get_active_session()
        return {
            "adb_devices": devices,
            "adb_path": self.adb.adb_path,
            "apk_exists": bool(self.apk_path and os.path.exists(self.apk_path)),
            "input_backend": self.touch.backend.name,
            "lan_ip": self.transport.lan_ip,
            "session_transport": active.transport_name if active else None,
            "session_peer": active.peer_name if active else None,
        }

    def _ensure_capture_worker(self):
        if self.capture_worker is not None:
            return
        from display import CaptureWorker, ScreenCapturer

        self.capture_worker = CaptureWorker(
            ScreenCapturer(scale=DEFAULT_SCALE, jpeg_quality=DEFAULT_JPEG_QUALITY),
            DEFAULT_FPS,
        )


def find_default_apk():
    candidate = os.path.join(
        os.path.dirname(os.path.dirname(__file__)),
        "android-app",
        "app",
        "build",
        "outputs",
        "apk",
        "debug",
        "app-debug.apk",
    )
    return candidate if os.path.exists(candidate) else candidate


def print_status(server):
    status = server.status()
    for key in sorted(status.keys()):
        print("%s: %s" % (key, status[key]))


def build_parser():
    parser = argparse.ArgumentParser(description="Pi Tablet Bridge server")
    parser.add_argument("command", nargs="?", default="connect", choices=["connect", "status", "install-apk"])
    parser.add_argument("--adb-path", default=None)
    parser.add_argument("--apk", default=find_default_apk())
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--discovery-port", type=int, default=DEFAULT_DISCOVERY_PORT)
    return parser


def main():
    args = build_parser().parse_args()
    server = BridgeServer(apk_path=args.apk, adb_path=args.adb_path, port=args.port, discovery_port=args.discovery_port)

    if args.command == "status":
        print_status(server)
        return 0

    if args.command == "install-apk":
        serial = server.adb.wait_for_device(timeout=10.0, poll_interval=0.5)
        if not serial:
            raise SystemExit("No connected adb device found")
        server.adb.with_serial(serial).install_apk(args.apk, replace=True)
        print("Installed %s on %s" % (args.apk, serial))
        return 0

    try:
        server.install_and_launch()
    except AdbError as exc:
        print("ADB setup warning: %s" % exc, file=sys.stderr)

    server.start()
    print("Pi Tablet Bridge running. Press Ctrl+C to stop.")
    try:
        while True:
            time.sleep(1.0)
    except KeyboardInterrupt:
        return 0
    finally:
        server.stop()


if __name__ == "__main__":
    raise SystemExit(main())
