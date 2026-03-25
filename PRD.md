# Pi Tablet Bridge — Product Requirements Document

**Project:** pi-tablet-bridge
**Repo:** https://github.com/msbel5/pi-tablet-bridge
**Version:** 0.1.0 (MVP)
**Date:** 2026-03-25

---

## Vision

Turn any USB-connected Android tablet into a **full I/O peripheral** for a Raspberry Pi — display, touchscreen, camera, microphone, speakers, and sensors — all accessible from Pi-side applications.

The tablet becomes an extension of the Pi, not an independent device. Think of it as a cheap, feature-rich touchscreen monitor that also has a camera, microphone, gyroscope, and more.

## Architecture

```
┌──────────────────────┐         USB + LAN         ┌──────────────────────┐
│   Raspberry Pi 5     │◄──────────────────────────►│   Android Tablet     │
│                      │                            │   (API 16+)          │
│  ┌────────────────┐  │    ADB (USB) primary       │  ┌────────────────┐  │
│  │ Bridge Server  │  │    TCP (LAN) fallback      │  │  Bridge APK    │  │
│  │ (Python)       │  │    Both = redundant         │  │  (Java/Kotlin) │  │
│  │                │  │                            │  │                │  │
│  │ • Screen push  │──┼─── JPEG frames ──────────►│  │ • Display      │  │
│  │ • Touch recv   │◄─┼─── Touch events ──────────│  │ • Touch fwd    │  │
│  │ • Cam recv     │◄─┼─── Camera frames ─────────│  │ • Camera cap   │  │
│  │ • Mic recv     │◄─┼─── Audio PCM ─────────────│  │ • Mic record   │  │
│  │ • Audio push   │──┼─── Audio PCM ──────────────│  │ • Speaker out  │  │
│  │ • Sensor recv  │◄─┼─── Sensor JSON ───────────│  │ • Sensor read  │  │
│  │ • Keyboard     │◄─┼─── Key events ────────────│  │ • Soft keyboard│  │
│  │ • Auto-setup   │──┼─── ADB install/launch ────│  │ • Auto-connect │  │
│  └────────────────┘  │                            │  └────────────────┘  │
│                      │                            │                      │
│  ┌────────────────┐  │                            │                      │
│  │ GUI (optional) │  │                            │                      │
│  │ Pi-side mgmt   │  │                            │                      │
│  └────────────────┘  │                            │                      │
└──────────────────────┘                            └──────────────────────┘
```

## Target Hardware

### Pi Side
- Raspberry Pi 5 (4GB+)
- Debian 13 Trixie (arm64)
- Python 3.11+
- ADB installed (`android-tools`)
- USB port for tablet connection
- WiFi on same LAN (optional, for redundancy)

### Tablet Side
- **Primary target:** Huawei MediaPad 10 Link S10-201u (Android 4.1, API 16, 1280x800, armeabi-v7a)
- **General:** Any Android tablet with API 16+ and USB debugging enabled
- Has: 10" touchscreen, rear camera, front camera, microphone, speaker, accelerometer, gyroscope, ambient light sensor, proximity sensor

## Functional Requirements

### FR-01: Auto-Discovery & Setup
- Pi-side app detects USB-connected tablets via `adb devices`
- If Bridge APK not installed → auto-install via `adb install`
- If APK installed but not running → auto-launch via `adb shell am start`
- Verify permissions (camera, mic, storage) → request if missing
- Display connection status in Pi-side terminal/GUI

### FR-02: Display Mirroring (Pi Screen → Tablet)
- Pi captures its own screen (via `grim`, `scrot`, or framebuffer)
- Encodes as JPEG and sends to tablet
- Tablet displays fullscreen, aspect-ratio preserved
- Target: 4-8 FPS at 640x360 resolution (half of 1280x720)
- Transport: TCP socket over USB (ADB port forward) or LAN
- Tablet touch events on the mirrored display map back to Pi mouse events

### FR-03: Touch Input (Tablet → Pi)
- Tablet forwards all touch events (x, y, pressure, finger count)
- Pi-side translates to mouse events (`xdotool` or `ydotool`)
- Multi-touch support (pinch-to-zoom mapped to scroll)
- Touch coordinates normalized to Pi screen resolution
- Tap = click, long press = right click, swipe = drag

### FR-04: Soft Keyboard (Tablet → Pi)
- When user taps a text area on mirrored display, tablet shows soft keyboard
- Key events forwarded to Pi as keyboard input (`xdotool key`)
- Support: letters, numbers, symbols, enter, backspace, arrow keys
- IME support for non-Latin input (Turkish, etc.)

### FR-05: Camera Capture (Tablet → Pi)
- Tablet captures camera frames (rear or front, selectable)
- Sends as JPEG frames over socket to Pi
- Pi-side receives as a virtual camera or saves frames
- Target: 2-5 FPS at 640x480 (bandwidth-friendly)
- Use case: Pi can "see" through tablet's camera

### FR-06: Microphone Audio (Tablet → Pi)
- Tablet records microphone audio
- Streams PCM/WAV data to Pi over socket
- Pi-side receives as a virtual audio input or saves to file
- Push-to-talk mode: button on tablet UI activates recording
- Continuous mode: always-on streaming (configurable)
- Sample rate: 16kHz mono (speech-optimized)

### FR-07: Speaker Audio (Pi → Tablet)
- Pi sends audio data to tablet
- Tablet plays through its speakers
- Use case: Pi can "speak" through tablet
- Format: PCM 16kHz mono or MP3
- Volume control from Pi side

### FR-08: Sensor Data (Tablet → Pi)
- Tablet reads all available sensors and streams data to Pi
- Sensors to support (if hardware present):
  - Accelerometer (x, y, z)
  - Gyroscope (rotation x, y, z)
  - Ambient light (lux)
  - Proximity (near/far)
  - Magnetometer/compass (heading)
  - Barometer (pressure)
  - Temperature (if available)
- Data format: JSON messages, configurable interval (100ms-5000ms)
- Pi-side API: `bridge.sensors.accelerometer.x` etc.

### FR-09: Dual Transport (USB + LAN)
- Primary: ADB USB port forwarding (fast, reliable, no network needed)
- Secondary: TCP over WiFi/LAN (when both on same network)
- If both available: use USB for latency-sensitive data (touch, display), LAN for bulk data (camera, audio)
- Auto-failover: if USB disconnects, switch to LAN; if LAN drops, wait for USB
- Heartbeat: 1-second ping to detect connection state

### FR-10: Pi-Side Management
- CLI tool: `pi-bridge status`, `pi-bridge connect`, `pi-bridge install-apk`
- Optional GUI: simple tkinter/web dashboard showing:
  - Connection status (USB/LAN/both)
  - Tablet battery level
  - Active streams (display, camera, mic, sensors)
  - FPS and latency metrics
  - Quick toggles for each feature

### FR-11: APK Self-Update
- Pi-side app can push new APK versions to tablet via ADB
- Tablet app checks version on connect and prompts if outdated
- `pi-bridge update-apk` command

## Non-Functional Requirements

### NFR-01: Android 4.1 Compatibility
- APK must target minSdkVersion 16 (Android 4.1)
- No Java 8+ features, no AndroidX (use support library v4 if needed)
- No modern APIs (Camera2, MediaCodec) — use deprecated Camera API
- Build with Gradle, standard Android SDK

### NFR-02: Low Resource Usage
- Pi-side: < 100MB RAM, < 10% CPU idle, < 30% CPU during display mirror
- Tablet-side: < 50MB RAM, smooth UI, no ANR
- Battery: tablet should last 4+ hours on battery with display active

### NFR-03: Resilience
- Auto-reconnect on any disconnection (USB replug, WiFi drop)
- Graceful degradation: if camera fails, other features continue
- No data loss: sensor readings buffered briefly if connection hiccups

### NFR-04: Security
- Communication only over USB (inherently secure) or local LAN
- No internet access required by APK (works fully offline)
- No authentication for v1 (trusted local network assumption)

## Project Structure

```
pi-tablet-bridge/
├── PRD.md                    # This document
├── README.md                 # Quick start guide
├── pi-side/                  # Raspberry Pi application
│   ├── bridge_server.py      # Main server orchestrating all channels
│   ├── display.py            # Screen capture → JPEG → tablet
│   ├── touch.py              # Touch events → mouse/keyboard
│   ├── camera.py             # Receive camera frames from tablet
│   ├── audio.py              # Mic receive + speaker send
│   ├── sensors.py            # Sensor data receiver
│   ├── transport.py          # USB (ADB) + LAN dual transport
│   ├── adb_manager.py        # Auto-discovery, install, launch
│   ├── gui.py                # Optional management GUI
│   ├── requirements.txt
│   └── tests/
├── android-app/              # Android APK source
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/pitabletbridge/
│   │   │   │   ├── MainActivity.java
│   │   │   │   ├── DisplayService.java      # Receive + render Pi screen
│   │   │   │   ├── TouchForwarder.java       # Forward touch events
│   │   │   │   ├── CameraService.java        # Capture + send camera
│   │   │   │   ├── AudioService.java         # Mic + speaker
│   │   │   │   ├── SensorService.java        # Read + send sensors
│   │   │   │   ├── TransportManager.java     # USB + LAN connection
│   │   │   │   └── KeyboardService.java      # Soft keyboard forwarding
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   ├── build.gradle
│   └── settings.gradle
└── docs/
    └── architecture.md
```

## MVP Scope (v0.1)

For the first release, implement in this order:

1. **Transport layer** (USB via ADB port forward + LAN TCP)
2. **Display mirroring** (Pi screen → tablet, JPEG stream)
3. **Touch input** (tablet touch → Pi mouse events)
4. **Soft keyboard** (tablet keyboard → Pi key events)
5. **Auto-setup** (detect tablet, install APK, launch, connect)

### Deferred to v0.2
- Camera capture
- Microphone/speaker audio
- Sensor streaming
- Management GUI
- Multi-tablet support

## Success Criteria (MVP)

- [ ] `pi-bridge connect` finds tablet, installs APK, launches it
- [ ] Pi screen visible on tablet within 3 seconds of connection
- [ ] Touch on tablet moves Pi mouse cursor
- [ ] Tap opens apps, long press right-clicks
- [ ] Soft keyboard types text into Pi applications
- [ ] Works over USB; works over LAN; works over both
- [ ] Auto-reconnects within 5 seconds of USB replug
- [ ] 4+ FPS display with < 200ms latency over USB

## Notes

- **Why not VNC?** Tried it — x11vnc crashes on Pi 5 (vc4 driver BadMatch), wayvnc framebuffer too large for old tablet, MultiVNC barely renders. Custom JPEG stream is lighter and more reliable.
- **Why not Spacedesk?** Requires Windows server. No Linux/Pi server exists.
- **Why not Scrcpy?** Scrcpy mirrors Android → PC, not the other direction.
- **Why custom APK?** Old tablet (API 16) can't run modern browsers properly. Native app with direct socket communication is faster and more reliable than web-based solutions.
