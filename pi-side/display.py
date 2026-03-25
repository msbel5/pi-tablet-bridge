import io
import subprocess
import threading
import time
from dataclasses import dataclass

from PIL import Image


@dataclass
class CapturedFrame:
    frame_id: int
    width: int
    height: int
    screen_width: int
    screen_height: int
    timestamp_ms: int
    jpeg_bytes: bytes


class ScreenCaptureError(RuntimeError):
    pass


class ScreenCapturer:
    def __init__(self, scale=0.5, jpeg_quality=50, grim_command="grim"):
        self.scale = scale
        self.jpeg_quality = jpeg_quality
        self.grim_command = grim_command
        self._frame_id = 0

    def capture_frame(self):
        try:
            proc = subprocess.run(
                [self.grim_command, "-s", str(self.scale), "-"],
                capture_output=True,
                check=False,
            )
        except FileNotFoundError:
            raise ScreenCaptureError("grim is not installed or not on PATH")

        if proc.returncode != 0:
            stderr = proc.stderr.decode("utf-8", errors="replace").strip()
            raise ScreenCaptureError("grim failed: %s" % (stderr or "unknown error"))

        image = Image.open(io.BytesIO(proc.stdout)).convert("RGB")
        buffer = io.BytesIO()
        image.save(buffer, format="JPEG", quality=self.jpeg_quality, optimize=False)

        self._frame_id += 1
        timestamp_ms = int(time.time() * 1000)
        scaled_width, scaled_height = image.size
        screen_width = max(1, int(round(float(scaled_width) / float(self.scale))))
        screen_height = max(1, int(round(float(scaled_height) / float(self.scale))))
        return CapturedFrame(
            frame_id=self._frame_id,
            width=scaled_width,
            height=scaled_height,
            screen_width=screen_width,
            screen_height=screen_height,
            timestamp_ms=timestamp_ms,
            jpeg_bytes=buffer.getvalue(),
        )


class LatestFrameStore:
    def __init__(self):
        self._lock = threading.Lock()
        self._latest_frame = None
        self._version = 0
        self._closed = False

    def publish(self, frame):
        with self._lock:
            if self._closed:
                return
            self._version += 1
            self._latest_frame = (self._version, frame)

    def get_if_newer(self, version):
        with self._lock:
            if self._latest_frame is None:
                return version, None
            latest_version, frame = self._latest_frame
            if latest_version <= version:
                return version, None
            return latest_version, frame

    def close(self):
        with self._lock:
            self._closed = True
            self._latest_frame = None


class CaptureWorker(object):
    def __init__(self, capturer, fps):
        self.capturer = capturer
        self.fps = fps
        self.frame_store = LatestFrameStore()
        self.stop_event = threading.Event()
        self.error_lock = threading.Lock()
        self.last_error = None
        self.thread = threading.Thread(target=self._run, name="capture-worker")
        self.thread.daemon = True

    def start(self):
        self.thread.start()

    def stop(self):
        self.stop_event.set()
        self.frame_store.close()
        if self.thread.is_alive():
            self.thread.join(timeout=2.0)

    def _run(self):
        interval = 1.0 / float(self.fps)
        while not self.stop_event.is_set():
            started = time.time()
            try:
                frame = self.capturer.capture_frame()
                self.frame_store.publish(frame)
                with self.error_lock:
                    self.last_error = None
            except Exception as exc:
                with self.error_lock:
                    self.last_error = exc
            elapsed = time.time() - started
            delay = max(0.0, interval - elapsed)
            self.stop_event.wait(delay)

    def get_last_error(self):
        with self.error_lock:
            return self.last_error

