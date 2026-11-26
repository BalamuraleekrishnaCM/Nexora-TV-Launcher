# Build Nexora TV Launcher

Use Android SDK 35, JDK 17 and Gradle 8.9. Open the project in Android Studio or build it from the repository root.

```sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
gradle :app:connectedDebugAndroidTest
```

The second command requires a connected Android TV device or emulator. Install the debug build with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Android TV verification

- Use the D-pad, OK and Back through Home, Apps, Inputs, Media, Widgets and Settings at 720p and 1080p. Check focus and scrolling at your TV's display size.
- Install a TV app and a sideloaded app with launcher activities. Refresh the app list, open both and long-press OK on a tile to toggle its favorite.
- Pick a large image from local or USB storage. Relaunch Nexora, reset the wallpaper and check Lite, Balanced and Premium modes.
- Save a weather city, check the returned region and forecast, then disconnect networking and verify cached conditions.
- Enable Nexora in notification access and play media. Check title, artist, artwork and supported transport actions. Revoke access and check the fallback.
- Open each detected TV input. Use the remote Input or Source key on firmware that does not expose a compatible handler.
- Select Nexora Ambient in system screensaver settings where the TV exposes third-party screensavers.

The debug APK is signed for testing. The release build is unsigned. An in-place upgrade of an existing installation requires the original signing certificate.
