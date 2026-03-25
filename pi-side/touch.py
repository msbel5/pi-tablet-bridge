import shutil
import subprocess
import threading


SPECIAL_KEY_MAP_XDOTOOL = {
    "enter": "Return",
    "backspace": "BackSpace",
    "tab": "Tab",
    "esc": "Escape",
    "left": "Left",
    "right": "Right",
    "up": "Up",
    "down": "Down",
}

SPECIAL_KEY_MAP_YDOTOOL = {
    "enter": 28,
    "backspace": 14,
    "tab": 15,
    "esc": 1,
    "left": 105,
    "right": 106,
    "up": 103,
    "down": 108,
}


def normalized_to_screen(norm_x, norm_y, screen_width, screen_height):
    norm_x = min(max(float(norm_x), 0.0), 1.0)
    norm_y = min(max(float(norm_y), 0.0), 1.0)
    x = int(round(norm_x * max(0, int(screen_width) - 1)))
    y = int(round(norm_y * max(0, int(screen_height) - 1)))
    return x, y


class InputBackendBase(object):
    name = "unavailable"
    available = False

    def move_absolute(self, x, y):
        raise NotImplementedError

    def left_click(self):
        raise NotImplementedError

    def right_click(self):
        raise NotImplementedError

    def button_down(self, button):
        raise NotImplementedError

    def button_up(self, button):
        raise NotImplementedError

    def type_text(self, text):
        raise NotImplementedError

    def press_special_key(self, key_name):
        raise NotImplementedError


class NoopBackend(InputBackendBase):
    name = "noop"
    available = False

    def __init__(self):
        self.commands = []

    def _record(self, name, *args):
        self.commands.append((name,) + args)

    def move_absolute(self, x, y):
        self._record("move_absolute", x, y)

    def left_click(self):
        self._record("left_click")

    def right_click(self):
        self._record("right_click")

    def button_down(self, button):
        self._record("button_down", button)

    def button_up(self, button):
        self._record("button_up", button)

    def type_text(self, text):
        self._record("type_text", text)

    def press_special_key(self, key_name):
        self._record("press_special_key", key_name)


class XdotoolBackend(InputBackendBase):
    name = "xdotool"
    available = True

    def __init__(self, binary="xdotool"):
        self.binary = binary

    def _run(self, args):
        subprocess.run([self.binary] + list(args), check=False)

    def move_absolute(self, x, y):
        self._run(["mousemove", "--sync", str(int(x)), str(int(y))])

    def left_click(self):
        self._run(["click", "1"])

    def right_click(self):
        self._run(["click", "3"])

    def button_down(self, button):
        self._run(["mousedown", "1" if button == "left" else "3"])

    def button_up(self, button):
        self._run(["mouseup", "1" if button == "left" else "3"])

    def type_text(self, text):
        self._run(["type", "--delay", "0", text])

    def press_special_key(self, key_name):
        mapped = SPECIAL_KEY_MAP_XDOTOOL.get(key_name.lower())
        if mapped:
            self._run(["key", mapped])


class YdotoolBackend(InputBackendBase):
    name = "ydotool"
    available = True

    def __init__(self, binary="ydotool"):
        self.binary = binary

    def _run(self, args):
        subprocess.run([self.binary] + list(args), check=False)

    def move_absolute(self, x, y):
        self._run(["mousemove", "--absolute", "-x", str(int(x)), "-y", str(int(y))])

    def left_click(self):
        self._run(["click", "0xC0"])

    def right_click(self):
        self._run(["click", "0xC1"])

    def button_down(self, button):
        code = "0x40" if button == "left" else "0x41"
        self._run(["click", code])

    def button_up(self, button):
        code = "0x80" if button == "left" else "0x81"
        self._run(["click", code])

    def type_text(self, text):
        self._run(["type", text])

    def press_special_key(self, key_name):
        code = SPECIAL_KEY_MAP_YDOTOOL.get(key_name.lower())
        if code is not None:
            self._run(["key", "%d:1" % code, "%d:0" % code])


def detect_input_backend():
    ydotool_path = shutil.which("ydotool")
    if ydotool_path:
        return YdotoolBackend(ydotool_path)
    xdotool_path = shutil.which("xdotool")
    if xdotool_path:
        return XdotoolBackend(xdotool_path)
    return NoopBackend()


class TouchDispatcher(object):
    def __init__(self, backend=None):
        self.backend = backend or detect_input_backend()
        self.screen_width = 1280
        self.screen_height = 720
        self.drag_active = False
        self.lock = threading.Lock()

    def update_screen_size(self, width, height):
        with self.lock:
            self.screen_width = max(1, int(width))
            self.screen_height = max(1, int(height))

    def _map_coordinates(self, norm_x, norm_y):
        return normalized_to_screen(norm_x, norm_y, self.screen_width, self.screen_height)

    def reset(self):
        with self.lock:
            if self.drag_active:
                self.backend.button_up("left")
            self.drag_active = False

    def handle_touch_event(self, payload):
        action = payload.get("action")
        norm_x = payload.get("x", 0.0)
        norm_y = payload.get("y", 0.0)

        with self.lock:
            x, y = self._map_coordinates(norm_x, norm_y)
            if action == "tap":
                self.backend.move_absolute(x, y)
                self.backend.left_click()
            elif action == "secondary_tap":
                self.backend.move_absolute(x, y)
                self.backend.right_click()
            elif action == "drag_start":
                self.backend.move_absolute(x, y)
                self.backend.button_down("left")
                self.drag_active = True
            elif action == "drag_move":
                if not self.drag_active:
                    self.backend.button_down("left")
                    self.drag_active = True
                self.backend.move_absolute(x, y)
            elif action == "drag_end":
                self.backend.move_absolute(x, y)
                if self.drag_active:
                    self.backend.button_up("left")
                self.drag_active = False

    def handle_key_event(self, payload):
        kind = payload.get("kind")
        if kind == "text":
            text = payload.get("text", "")
            if text:
                self.backend.type_text(text)
        elif kind == "backspace":
            count = int(payload.get("count", 1))
            for _ in range(max(1, count)):
                self.backend.press_special_key("backspace")
        elif kind == "special":
            key_name = payload.get("key")
            if key_name:
                self.backend.press_special_key(str(key_name))

