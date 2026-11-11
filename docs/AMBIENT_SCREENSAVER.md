# Nexora Ambient Screensaver — Feature specification

Status: planned, not implemented.

## Goals
- Match the launcher visual language while keeping memory, CPU and GPU overhead low.
- Provide a full-screen ambient experience with clock/date, optional cached weather, optional media metadata, and wallpaper slideshow.
- Never interrupt active playback or unexpectedly replace an HDMI source.

## Modes
1. Scenic: cached static nature/space wallpapers, clock and optional weather.
2. AMOLED minimal: dark background, movable clock, minimal illumination.
3. Personal photos: user-selected images via Storage Access Framework.

## Settings
- Idle timeout presets: 5, 10, 15, 30 minutes; default 10.
- Slideshow interval: 1, 5, 10 minutes; default 5.
- Toggle clock/date, weather, now playing.
- Dimming, clock placement, fade duration, motion reduction, and screen wake behavior.
- Offline-first with bounded cache shared with Daily Wallpaper.

## Android implementation
- Implement a dedicated Android DreamService, declare it with android.service.dreams.DreamService and appropriate dream metadata/configuration activity.
- Offer user instructions to select Nexora in Android TV screensaver settings when firmware exposes this setting; do not assume third-party dreams are selectable on every Xiaomi firmware.
- Separately provide an optional launcher-only inactivity overlay if DreamService selection is unavailable.
- In launcher-only mode, count input inactivity only while Nexora is foregrounded; do not install a global input interceptor or disrupt other apps.
- Respect system screen timeout, power-saving and sleep settings; do not hold wake locks.
- System DreamService should be governed by the OS, with no claim of activation while any particular media app is playing.
- Restore previous launcher focus after dismissing the overlay.
- Avoid network access during the screensaver; reuse cached weather and wallpaper.
- Decode images to display resolution, dispose old bitmaps, avoid live wallpaper/video shaders, and reduce OLED static-content burn-in risk by moving clock subtly.

## Acceptance criteria
- Remote-only configuration and dismissal work.
- Does not trigger from launcher overlay during active video or HDMI playback.
- No background timer service continuously polling when launcher is not visible.
- No crashes on offline startup or unavailable weather/media data.
- DreamService support is verified on target Xiaomi model and Android version; fallback behavior documented.
- Idle memory usage, frame time and cache size measured on device.
