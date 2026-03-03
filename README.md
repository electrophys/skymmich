# skymmich

A native Android photo frame app for [Immich](https://immich.app/), built for the Skylight D106 digital photo frame.


## Features

- **Fullscreen slideshow** with crossfade transitions
- **Random photos** from your Immich library, loaded in batches
- **Motion-aware brightness** — dims after 5 min idle, sleeps after 10 min, wakes on movement
- **Triple-tap settings** — tap/shake the frame 3 times to open the settings overlay
- **Minimal memory footprint** — RGB_565 bitmaps, 20MB memory cache, ~55-65MB peak usage
- **Self-signed cert support** — works with local HTTPS servers without trusted CA

## Hardware

| | |
|---|---|
| **Device** | Skylight D106 |
| **SoC** | Rockchip RK3128, ARM Cortex-A7 |
| **ABI** | armeabi-v7a |
| **Android** | 7.1.2 (API 25) |
| **RAM** | 1GB |
| **Display** | 1280x800, landscape |
| **Network** | USB Ethernet (RTL8152) if WiFi chip is dead |

## Build

Requires JDK 17 and Android SDK with platform 28 and build-tools 30.0.3.

```bash
ANDROID_HOME=~/android-sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n red.henry.skymmich/.MainActivity
```

## Setup

On first launch (or after triple-tap), the settings overlay appears:

1. **Server URL** — your Immich instance
2. **API Key** — generate one in Immich under User Settings → API Keys
3. **Interval** — seconds between photos (default 30, minimum 5)

Tap **Save & Start** to test the connection and begin the slideshow.

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| Glide | 4.16.0 | Image loading, caching, downsampling |
| OkHttp | 4.12.0 | HTTP client, Glide network backend |
| org.json | (built-in) | JSON parsing for API responses |


