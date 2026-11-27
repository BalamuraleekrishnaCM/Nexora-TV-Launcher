# Nexora TV Launcher

Android TV / Google TV launcher source, Kotlin and Jetpack Compose. Version 1.0.2 development candidate; no verified stable release.

## Implemented in source
- D-pad-oriented home, favorites and installed TV/mobile app discovery.
- Clock, date, network state and Open-Meteo weather with manual city, cache and refresh.
- Glass-inspired themes and local wallpaper selection.
- Now Playing metadata and media button routing with notification access consent and system fallback.
- OEM-dependent TV input shortcut and ambient DreamService.

## Build
JDK 17, Android SDK 35, Gradle 8.9. Run `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. GitHub Actions uploads a debug APK only on successful CI.
See [build details](README-BUILD.md) and [device QA](docs/DEVICE_QA.md).

## Limitations
Direct HDMI switching, default launcher selection, metadata access and screensaver availability depend on OEM firmware. Keep stock launcher installed. Physical TV tests and signed release remain pending.
