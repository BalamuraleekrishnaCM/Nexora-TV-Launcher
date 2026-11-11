# Release roadmap

All milestones below are **planned**, not completed. A version is released only when its acceptance gates pass.

## v0.1.0 — Foundation
- [ ] Create Android Studio Kotlin/Gradle project with Compose for TV
- [ ] Implement TV launcher intent filters and an initial Home screen
- [ ] Add focusable cards, D-pad/OK/Back behavior and focus restoration
- [ ] Add baseline design tokens, typography and themes
- [ ] Add unit tests and CI debug-build workflow
**Gate:** Build and launch on TV emulator; navigate entire screen using remote only.

## v0.2.0 — Six core widgets
- [ ] Clock and date with 12/24-hour preferences
- [ ] Weather provider interface, permissions/configuration and offline fallback
- [ ] Installed TV apps and editable favorites
- [ ] Wi-Fi connectivity indicator with permission-safe fallbacks
- [ ] Calendar month/date and optional events source
- [ ] Now-playing metadata and permitted media controls
**Gate:** Each widget has loading/empty/error states; no remote focus traps.

## v0.3.0 — Customization
- [ ] Dashboard, app-first and minimal layout presets
- [ ] Dark glass, light minimal and AMOLED themes
- [ ] Widget visibility and reordering
- [ ] Wallpaper and transparency options
- [ ] Persist preferences in DataStore
**Gate:** Settings survive reboot; restore-defaults action works.

## v0.4.0 — Android TV integration
- [ ] Reliable app discovery and launch with package visibility policy
- [ ] Verify launcher selection/Home behavior on supported devices
- [ ] MediaSession integration and playback permissions
- [ ] Handle Wi-Fi/location/calendar permissions transparently
- [ ] Xiaomi TV and Google TV compatibility matrix
**Gate:** Safe fallback to stock launcher and no unsupported permission assumptions.

## v0.5.0 — Public beta
- [ ] Physical Xiaomi TV testing, including cold boot and remote navigation
- [ ] Accessibility: focus contrast, font scaling and TalkBack
- [ ] Startup time, memory and UI smoothness measurements
- [ ] Automated smoke tests and crash regression tests
- [ ] Publish beta APK and known-issues list
**Gate:** No release-blocking crashes; documented installation/recovery steps.

## v1.0.0 — Stable
- [ ] Full regression test and device compatibility report
- [ ] Reproducible release build and secure signing process
- [ ] README screenshots, installation guide and FAQ
- [ ] CHANGELOG and GitHub Release with checksums
- [ ] License and third-party notices
**Gate:** Signed APK, passing CI, tested recovery and complete documentation.

## Future candidates (not committed)
- Localization including Malayalam
- Backup/import/export of layout profiles
- Optional ambient screensaver
- Additional widgets and plugin interfaces

## Versioning
Use Semantic Versioning. Tag only tested deliverables; avoid publishing a release tag for a roadmap entry alone.
