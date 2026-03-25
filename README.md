# Pi Tablet Bridge

Turn an old Android tablet into a Pi display and input device over USB or LAN.

## What is implemented

- TCP transport with USB primary (`adb forward`) and LAN fallback.
- Pi screen capture with `grim`, Pillow JPEG conversion, and frame streaming.
- Tablet fullscreen frame rendering with latest-frame decode/drop behavior.
- Tablet touch gestures forwarded to Pi mouse actions.
- Tablet keyboard dialog forwarding text and special keys.
- Pi auto-setup for detect/install/launch/forward.

Deferred features from `PRD.md` remain out of scope for this MVP: camera, microphone, speakers, sensors, and GUI management.

## Repo layout

- `pi-side/` Python bridge server.
- `android-app/` Java/Gradle Android project.
- `PRD.md` product requirements and scope.

## Pi-side requirements

- Python 3.10+ (target runtime is Python 3.11+ on the Pi).
- `grim`
- `ydotool` plus `ydotoold` on Wayland, or `xdotool` on X11
- ADB available at `/usr/lib/android-sdk/platform-tools/adb` or on `PATH`
- Pillow installed: `pip install -r pi-side/requirements.txt`

## Android build requirements

- JDK 11 available locally.
- Android SDK Platform 28 and Build Tools 30.0.3.
- `android-app/gradlew` bootstraps Gradle 6.7.1 automatically.

The wrapper script prefers `/usr/lib/jvm/java-11-openjdk-amd64` when `JAVA_HOME` is not set. If your SDK lives outside the repo, export `ANDROID_HOME` and `ANDROID_SDK_ROOT`.

## Build the APK

```bash
cd android-app
export ANDROID_HOME="$(pwd)/../.android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew testDebugUnitTest assembleDebug
```

Debug APK output:

```bash
android-app/app/build/outputs/apk/debug/app-debug.apk
```

Install to the tablet:

```bash
/usr/lib/android-sdk/platform-tools/adb install -r android-app/app/build/outputs/apk/debug/app-debug.apk
/usr/lib/android-sdk/platform-tools/adb shell am start -n com.pitabletbridge/.MainActivity
```

## Run the Pi bridge

```bash
cd pi-side
python3 -m unittest discover tests
python3 bridge_server.py connect
```

Useful commands:

```bash
python3 bridge_server.py status
python3 bridge_server.py install-apk
python3 bridge_server.py connect --apk ../android-app/app/build/outputs/apk/debug/app-debug.apk
```

## Runtime notes

- USB transport uses the tablet-side `ServerSocket` on port `19876`.
- LAN discovery uses UDP broadcast on `19877`.
- The Pi binds LAN TCP on its detected LAN IP so it can still use `adb forward tcp:19876 tcp:19876` on `127.0.0.1`.
- If `ydotool` or `xdotool` is missing, display streaming still runs but input injection is degraded.
