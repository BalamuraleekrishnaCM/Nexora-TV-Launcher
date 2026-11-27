# Build and verification

Install JDK 17, Android SDK 35 and Gradle 8.9. No wrapper is currently committed.
Run `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
CI uploads a debug APK if all tasks succeed. No build or APK is claimed verified until the workflow passes. Do not use debug builds as production releases.
See [device QA](docs/DEVICE_QA.md).
