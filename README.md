# Android ESP Port (proof of concept)

Standalone Kotlin Android overlay with C++17 JNI world-to-screen projection ported from `esp/Core/UnityMath.mm` in the supplied iOS repository. The APK includes a KernelSU `su -c id` authorization test.

**Important:** The overlay currently draws synthetic test entities. It does not access Free Fire memory, discover players, inject into the game, or provide live ESP. iOS Mach memory APIs are not portable to Android. Original hard-coded offsets are version-dependent and unverified.

## Build
Upload this folder to GitHub and run **Actions → Build Android APK → Run workflow**. Download `ESPPort-debug-apk` from the workflow run. Alternatively run `gradle assembleDebug` with Gradle 8.9, Android SDK 35, NDK 27.2 and JDK 17.

## Test
Install the APK, allow overlay permission, press Start sample overlay. Check KernelSU root separately. Stop from the app.

## Next steps
Use a documented or controlled data source for entity positions, and add a bounded IPC protocol to transport entities to the overlay. Add near-plane clipping, orientation handling, and dynamic display resolution before using real 3D coordinates.
