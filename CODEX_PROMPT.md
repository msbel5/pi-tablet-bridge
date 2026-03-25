# Codex Task Prompt

## Context

I have a Raspberry Pi 5 (Debian 13 Trixie, arm64) with an old Android tablet (Huawei MediaPad 10 Link S10-201u) connected via USB. The tablet runs **Android 4.1.2 (API 16)** with an **armeabi-v7a** CPU and **1280x800** screen.

ADB works from Pi to tablet:
```bash
$ adb devices
HWS10201u0000000000	device
$ adb shell getprop ro.build.version.sdk
16
```

## What I Need

Build the **pi-tablet-bridge** project as described in `PRD.md`. This turns the tablet into a full I/O peripheral for the Pi — display mirror, touchscreen input, keyboard, and auto-setup.

## Technical Constraints (CRITICAL)

### Android Side (APK)
- **minSdkVersion 16** — this is Android 4.1 Jelly Bean. This is non-negotiable.
- **NO AndroidX** — use `android.support.v4` if needed, or no support library at all
- **NO Java 8 lambdas** — the old Android toolchain doesn't support them. Use anonymous inner classes.
- **NO Camera2 API** — use the deprecated `android.hardware.Camera` class
- **NO MediaCodec** for encoding — just raw JPEG via `Camera.takePicture()` or `YuvImage`
- **NO WebSocket libraries** — use plain `java.net.Socket` for TCP communication
- **NO OkHttp, Retrofit, Gson** — use `HttpURLConnection` and `org.json` (built-in)
- **Target SDK can be 28** but must run on API 16
- Build with Gradle. Standard Android project structure.
- APK must be installable via `adb install`

### Pi Side (Python)
- Python 3.11+
- Screen capture: use `grim` (Wayland) → outputs PNG to stdout. Convert to JPEG with Pillow.
  ```bash
  grim -s 0.5 -  # captures half-resolution PNG to stdout
  ```
  Note: `grim` does NOT support JPEG output (compiled without libjpeg). You MUST use Pillow to convert RGBA PNG → RGB JPEG.
- Mouse control: use `ydotool` (Wayland) or `xdotool` (X11). Pi runs Wayland (labwc).
  ```bash
  ydotool mousemove --absolute -x 500 -y 300
  ydotool click 1  # left click
  ydotool type "hello"  # keyboard input
  ```
- ADB: installed at `/usr/lib/android-sdk/platform-tools/adb`, version 34.0.5
- Dependencies: keep minimal. Pillow is already installed. Avoid heavy frameworks.

### Transport
- **ADB port forwarding** is the primary transport:
  ```bash
  adb forward tcp:19876 tcp:19876  # Pi port 19876 → tablet port 19876
  ```
  This lets Pi connect to `localhost:19876` and reach the tablet app's server socket.

  For reverse (Pi server, tablet client):
  ```bash
  adb reverse tcp:19876 tcp:19876  # FAILS on API 16 — adb reverse not supported
  ```
  **`adb reverse` does NOT work on this tablet.** Use `adb forward` only, meaning the tablet runs a server socket and Pi connects to it. OR use LAN where tablet connects to Pi's IP.

- **LAN TCP** as secondary: both devices on same WiFi (192.168.1.x). Tablet connects to Pi IP.
- Protocol: simple binary/JSON over TCP. No HTTP overhead. Example:
  ```
  [4 bytes: msg type][4 bytes: payload length][payload]
  ```
  Message types: FRAME, TOUCH, KEY, SENSOR, AUDIO, PING, CONFIG

### What already works (don't reinvent)
- JPEG screen capture from Pi works:
  ```python
  proc = subprocess.run(['grim', '-s', '0.5', '-'], capture_output=True)
  img = Image.open(io.BytesIO(proc.stdout)).convert('RGB')
  buf = io.BytesIO()
  img.save(buf, format='JPEG', quality=50)
  jpeg_bytes = buf.getvalue()  # ~40-50KB per frame
  ```
- ADB install + launch from Pi works:
  ```bash
  adb install /path/to/bridge.apk
  adb shell am start -n com.pitabletbridge/.MainActivity
  ```

## MVP Implementation Order

1. **Transport layer** — TCP socket communication between Pi and tablet
2. **Display mirroring** — Pi sends JPEG frames, tablet renders fullscreen
3. **Touch forwarding** — Tablet sends touch events, Pi executes mouse actions
4. **Keyboard forwarding** — Tablet soft keyboard → Pi key events
5. **Auto-setup** — Pi script detects tablet, installs APK, connects

## Deliverables

1. `pi-side/` — Complete Python application, runnable with `python bridge_server.py`
2. `android-app/` — Complete Android project, buildable with `./gradlew assembleDebug`
3. `README.md` — How to build, install, and use
4. Working APK file if possible

## Quality Bar

- Code must compile and run. No stubs, no "TODO" placeholders, no mock implementations.
- Test on actual API 16 device assumptions (no modern API calls).
- Handle disconnections gracefully — auto-reconnect.
- Display should achieve 4+ FPS over USB with < 200ms latency.
