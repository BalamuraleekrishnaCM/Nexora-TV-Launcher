# UI, TV inputs and performance requirements

## Design direction
Preserve the approved dark mountain/sunset wallpaper concept: restrained translucent cards, clear focus glow, readable typography and a single screen with clock, weather, calendar, favorites, TV inputs and now-playing. Avoid content recommendations, advertising, autoplay video and unnecessary visual noise. Offer reduced transparency and reduced motion for low-end hardware.

## Home screen
1. Header: launcher name, network indicator, settings and time.
2. Top row: clock/date, weather and calendar.
3. Favorite apps: large remote-focusable tiles and All Apps.
4. TV & Media Inputs: HDMI 1, HDMI 2, Cable/Live TV, USB Media; configurable labels, visibility and order.
5. Now Playing: metadata and transport controls when supported.
6. Optional Wi-Fi detail widget and layout editing.

## Input switching feasibility
Android TV HDMI switching is OEM- and firmware-dependent. Implement an input abstraction with capabilities:
- Detect available TV input services using public APIs where allowed.
- Prefer launching an exported OEM input selector / TV input app or a user-configured installed app.
- Where supported, use documented TV input integration; do not claim direct HDMI switching without device validation.
- If unavailable, show a guided fallback to the TV's physical remote Input/Source button.
- Never require root, privileged signatures, hidden APIs or accessibility abuse.
- Treat USB media as a shortcut to a compatible media browser, not raw filesystem access without user permission.

## Free data and offline behavior
- Time/date: Android system clock and timezone; no network required.
- Weather: Open-Meteo forecast API for permitted noncommercial usage, respecting attribution and terms; user-chosen location, refresh interval >=30 minutes, persisted cache and offline fallback. Review license and commercial-use conditions before distribution.
- Calendar: local date via java.time; optional Calendar Provider only after explicit permission.
- Wi-Fi: ConnectivityManager / WifiManager within platform privacy limits; display unknown/unavailable when restricted.
- Now Playing: MediaSessionManager or accessible media controllers, respecting notification-listener/user authorization as applicable.
- Installed apps: PackageManager launchable TV activities, filtered to relevant packages.

## Performance budgets (aspirational; measure on real TV)
- Idle process PSS: target <100 MB, stretch 50–80 MB; measure via adb shell dumpsys meminfo.
- Release APK: target <15 MB; size varies with bundled dependencies.
- Cold-start to usable Home: target <2 seconds on supported target hardware.
- No permanent polling loops, background location, autoplay previews or animated wallpaper.
- Cache weather, load icons lazily, downsample wallpaper and use stable Compose state.
- Avoid network work on the main thread; pause nonessential updates when backgrounded.
- CI build/test gates, startup profiling and physical remote tests before public release.

## Accessibility and compatibility
- Full D-pad and Back navigation, predictable focus restoration, visible focus states.
- Text scaling, high contrast and reduced motion.
- Xiaomi TV model, Android TV version, input app package and available HDMI sources must be tested before promising direct switching.
- Keep stock launcher recoverable.
