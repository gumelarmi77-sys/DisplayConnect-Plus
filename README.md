# DisplayConnect+

## Overview

DisplayConnect+ is an Android application paired with an ESP32‑CYD board that displays notifications, call alerts, media controls, and simple navigation HUD/map on a small screen via Bluetooth Low Energy (BLE).

- **No Android Studio required** – the project builds with the Gradle wrapper and GitHub Actions.
- **No external Google Maps API key** – navigation data is extracted from Android notifications (Google Maps, Waze, etc.) and sent to the ESP32 as JSON.
- **ESP32 firmware** parses the JSON and shows either a HUD style text overlay or a simple map on the CYD display, with touch‑controlled media buttons.

## Repository Structure

```
DisplayConnect-Plus/
├─ app/                  # Android source code (Kotlin)
│   ├─ src/main/java/...   (BLE, notification service, UI)
│   └─ src/main/res/...    (layouts, icons)
├─ DisplayConnect_CYD/   # Arduino sketch for ESP32‑CYD
│   └─ DisplayConnect_CYD.ino
├─ .github/workflows/    # GitHub Actions CI to build the APK
│   └─ build-apk.yml
├─ gradle/               # Gradle wrapper files
├─ .gitignore
└─ README.md
```

## Prerequisites

- **JDK 17** (or later) – required by the Gradle wrapper.
- **Git** – for version control.
- **Arduino IDE** (or PlatformIO) with the following libraries installed:
  - `LovyanGFX`
  - `ArduinoJson`
- **Android device** with BLE, location, and notification‑access permissions.
- **ESP32‑CYD board** with the screen wired (backlight GPIO 21 or 27).

## Building the Android App Locally

```bash
# From the repo root
./gradlew clean assembleDebug
```

The generated APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions Build (CI)

The workflow `.github/workflows/build-apk.yml` runs on every push to `main` and produces the same `app-debug.apk` as an artifact.

## Deploying the ESP32 Firmware

1. Open `DisplayConnect_CYD/DisplayConnect_CYD.ino` in the Arduino IDE.
2. Select **ESP32 Dev Module** (or the appropriate board).
3. Choose the correct **Flash Frequency**, **Upload Speed**, and **Port**.
4. Click **Upload**.

## Usage

1. Install the APK on your Android device and grant **Notification Access**, **Location**, and **Bluetooth** permissions.
2. Launch the app, tap **Scan & Connect** to pair with the ESP32 board.
3. The ESP32 will display incoming WhatsApp messages, call alerts, media controls, and navigation data.
4. In the **Navigation** screen you can start a navigation session; the app extracts coordinates from the Google Maps (or compatible) notification and sends them to the ESP32.
5. Touch the top‑right corner of the screen on the ESP32 to toggle between HUD text and a simple map view.

## Contributing

Feel free to open issues or submit pull requests. Ensure that any new code follows the existing structure and updates the corresponding ESP32 JSON handling if new notification types are added.

## License

This project is licensed under the **MIT License**.
