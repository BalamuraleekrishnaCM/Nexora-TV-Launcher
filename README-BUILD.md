# Build status

This is an initial runnable **pre-alpha** Android TV source project, not the full final product.

## Build

Install Android Studio with Android SDK 35, JDK 17 and Gradle 8.9, then open the root folder and build `:app:assembleDebug`. The repository intentionally does not contain a generated Gradle wrapper; the CI workflow installs Gradle 8.9 explicitly.

## Current implementation

- Compose-based TV home with D-pad focusable tiles
- Clock/date, local calendar date and basic network state
- Open-Meteo weather for a temporary Kochi coordinate (not user-configurable yet)
- Installed leanback app discovery, launch and favorite-by-long-press
- Live TV/source fallback shortcut, USB picker and casting settings shortcut
- Dark/light toggle and weather visibility preference
- Minimal Android DreamService clock screensaver

## Not yet implemented

Daily wallpaper rotation, real HDMI port discovery/switching, real media playback controls, configurable location, full calendar events, custom widget arrangement, production-grade artwork, weather caching, remote hardware testing, signing and production APK release.

## Important limitations

Android TV OEMs may restrict choosing a third-party launcher or DreamService. The TV/source button fallback is device-dependent. No system privileges are used. The application has not been compiled in this environment; CI results are the source of build validation.
