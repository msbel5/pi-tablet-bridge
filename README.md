# Pi Tablet Bridge

Turn an Android tablet into a full I/O peripheral for Raspberry Pi — touchscreen display, camera, microphone, speakers, and sensors, all over USB + LAN.

## Why?

Old Android tablets (even Android 4.1) have hardware that's still useful: 10" touchscreen, cameras, microphone, speaker, gyroscope. This project makes all of that accessible from a Raspberry Pi, turning a $0 tablet into a $200 peripheral.

## Status

**Early development** — see [PRD.md](PRD.md) for full requirements.

## Hardware

- **Pi:** Raspberry Pi 5, Debian 13 Trixie
- **Tablet:** Huawei MediaPad 10 Link S10-201u (Android 4.1, API 16)
- **Connection:** USB cable + optional WiFi LAN

## Quick Start

```bash
# Pi side
cd pi-side
pip install -r requirements.txt
python bridge_server.py

# The script will:
# 1. Detect connected tablet via ADB
# 2. Install the Bridge APK if needed
# 3. Launch the app on tablet
# 4. Start mirroring Pi screen to tablet
# 5. Forward tablet touch events to Pi
```

## License

MIT
