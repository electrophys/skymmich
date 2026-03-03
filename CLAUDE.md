# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Deploy

```bash
# Build (ANDROID_HOME and JAVA_HOME are set by the devcontainer)
./gradlew assembleDebug

# Install via USB ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n red.henry.skymmich/.MainActivity
```

Build tools: AGP 7.4.2, Gradle 7.5.1, Build Tools 30.0.3, compileSdk 28, minSdk/targetSdk 25.

## Target Device

Skylight D106 digital photo frame: RK3128 (ARM Cortex-A7), Android 7.1.2, 1GB RAM, 1280x800 landscape, armeabi-v7a only. Dead WiFi chip — network via USB Ethernet adapter (RTL8152) brought up by `/data/local/tmp/eth_up.sh` which the app runs via `su` on startup. Only one USB port: either ADB or Ethernet, never both simultaneously. After deploying via USB, swap to Ethernet adapter and reboot to test.

## Architecture

Single Activity app (`MainActivity`) orchestrating four controller classes — no Fragments, no ViewModels, plain Java.

**Startup flow:** MainActivity → `bringUpNetwork()` (runs `eth_up.sh` via su if no interfaces up) → `onNetworkReady()` → shows settings or starts slideshow based on `SettingsManager.isConfigured()`.

**Slideshow flow:** `SlideshowController` fetches batches of 20 random asset IDs from Immich API, queues them, loads preview images into alternating front/back `ImageView`s with 1.5s crossfade. Refills queue at <5 remaining.

**Image pipeline:** Glide 4.16 + OkHttp 4.12. `ImmichGlideModule` configures OkHttp integration with API key interceptor, 20MB memory cache, 100MB disk cache, RGB_565 format. Images downsampled to 1280x800 — critical for 1GB RAM budget.

**Immich API:** `ImmichApi` talks to two endpoints — `POST /api/search/random` (preferred) with fallback to `GET /api/assets/random`, and `GET /api/assets/{id}/thumbnail?size=preview` for image loading. Auth via `x-api-key` header injected by OkHttp interceptor.

**Motion/brightness:** `MotionWakeController` reads accelerometer at SENSOR_DELAY_NORMAL. Detects motion (>0.5 m/s² delta) for wake, idle timeouts for dim (5min→15%, 10min→1%), and triple-tap (3 spikes >3.0 m/s² in 1s) to toggle settings overlay.

## Key Constraints

- **Memory:** ~55-65MB peak budget. Two 1280x800 RGB_565 bitmaps (~2MB each) max in memory. Glide caches are sized accordingly.
- **SSL:** Trust-all certificate configuration in `buildHttpClient()` — intentional for local network with self-signed certs.
- **Network bringup:** App runs `eth_up.sh` as root via `su` stdin pipe (`Runtime.getRuntime().exec("su")`). The script calls `setprop sys.usb.config none` (kills USB device mode), waits for eth0, runs dhcptool, then configures routing/DNS via `ndc` commands.
- **No AndroidX usage in app code:** Despite `android.useAndroidX=true` in gradle.properties (required by Glide's transitive deps), app code uses `android.app.Activity` and standard Android framework classes.
