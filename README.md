# Nexora TV Launcher

A lightweight, minimalist, customizable Android TV / Google TV home launcher by **Balamuraleekrishna C.M.**

> **Status:** Planning / pre-alpha. No installable APK or production-ready launcher is available yet.

## Vision

Build a distraction-free, remote-first TV home screen with a modern glass-inspired interface, configurable widgets, and fast access to installed apps. Designed initially for Xiaomi Android TV hardware, with broader Android TV / Google TV compatibility as a goal.

## Planned features

| Widget | Scope |
| --- | --- |
| Clock | Local time, date, 12/24-hour format |
| Weather | Current conditions and forecast via configurable provider; graceful offline state |
| Favorite apps | Discover, pin, reorder and launch installed TV apps |
| Wi-Fi | Connectivity and network information subject to Android permissions |
| Calendar | Local date, month view, optional calendar integration |
| Now playing | Media metadata, artwork and playback actions where media sessions permit |

### Remote-first navigation

- D-pad to move focus across widgets and app tiles
- OK to select; Back to dismiss or navigate back
- Long-press OK for context actions where supported
- Visible, accessible focus indicators and predictable focus order
- Home-key/default-launcher behavior depends on device firmware and system settings

### Customization

- Dashboard, app-first and minimal layouts
- Dark glass, light minimal and AMOLED themes
- Wallpaper selection, transparency and widget visibility
- Reorderable favorites and persistent preferences
- Respect reduced-motion and accessibility settings

## Proposed technology

- **Language:** Kotlin
- **UI:** Jetpack Compose for TV
- **Architecture:** MVVM with repositories and unidirectional UI state
- **Persistence:** Jetpack DataStore
- **Integrations:** Android package discovery, MediaSession/MediaController, connectivity APIs, optional weather and calendar providers
- **Build:** Gradle Kotlin DSL, Android Studio, GitHub Actions
- **Testing:** Unit tests, Compose UI tests, physical Xiaomi TV remote tests

Dependencies, SDK levels, permissions and device support will be validated during the foundation phase rather than assumed.

## Development roadmap

| Version | Stage | Planned deliverables |
| --- | --- | --- |
| v0.1.0 | Foundation | Gradle project, launcher activity, theme, home UI, D-pad focus, tests |
| v0.2.0 | Widgets | Six widget modules with loading, empty and error states |
| v0.3.0 | Customization | Layout presets, themes, wallpaper, widget settings, DataStore |
| v0.4.0 | Integration | App launch/discovery, media controls, Wi-Fi details, launcher-role validation |
| v0.5.0 | Beta | Xiaomi device tests, accessibility, performance, crash fixes, beta APK |
| v1.0.0 | Stable | Release QA, signed APK, changelog, screenshots, compatibility docs |

Detailed acceptance criteria: [ROADMAP.md](ROADMAP.md).

## Planned project structure

```text
app/                         # Android TV launcher entry point
core/designsystem/           # Themes, components, focus styling
core/model/                  # Shared models
core/data/                   # Preferences and platform repositories
feature/home/                # Home screen and layout engine
feature/apps/                # Installed apps and favorites
feature/widgets/clock/
feature/widgets/weather/
feature/widgets/wifi/
feature/widgets/calendar/
feature/widgets/media/
feature/settings/            # Launcher customization
docs/                        # Architecture, compatibility, screenshots
.github/workflows/           # CI and release checks
```

This is a target structure, not a claim that these modules already exist.

## Build and installation

The Android Studio project has **not yet been committed**. Once v0.1.0 lands:

1. Clone: `git clone https://github.com/BalamuraleekrishnaCM/Nexora-TV-Launcher.git`
2. Open in Android Studio and sync Gradle.
3. Build the debug APK and test on an Android TV emulator.
4. Install on a Xiaomi TV using ADB and test remote focus/navigation.
5. Select Nexora as Home only if supported by the device's launcher settings.

Do not disable or uninstall the stock launcher until recovery and fallback behavior are verified.

## Release policy

- Semantic Versioning: `MAJOR.MINOR.PATCH`
- Development releases tagged `v0.x.y`; stable release `v1.0.0`
- Release only after build, tests, remote-navigation checks and device validation
- APKs and changelogs published under GitHub Releases once available
- Never commit signing keys, API keys or device credentials

## Contributing

Issues and pull requests are welcome once the initial source is available. Please include the TV model, Android version, steps to reproduce and relevant logs for bug reports.

## License

MIT license planned; add a LICENSE file before distributing source under that license.

**Maintainer:** [BalamuraleekrishnaCM](https://github.com/BalamuraleekrishnaCM)
