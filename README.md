# DUNE — Native Android phone project

Kotlin + Jetpack Compose starter for Android phones (portrait, minSdk 26, target/compileSdk 35).

## Included in this iteration
- Desert-themed Compose screen with layered dune Canvas and optional rotation-vector parallax when a gyroscope is available.
- Usage Access onboarding and Preview mode.
- UsageStats-based app inactivity estimates, local Room database, and daily WorkManager refresh.
- Swipe-to-clean mask that accumulates cleared areas, plus a capped 200-particle sand effect.
- Haptic feedback and lightweight system tone effects for brushing, polish completion, and rescue.
- Polish boost, rescue launch intent, and Android uninstall confirmation intent.
- GitHub Actions workflow to build a debug APK.

## Build the APK on GitHub (no Android Studio needed)
1. Create a new GitHub repository and upload **the contents** of this folder (so `app/`, `.github/`, `build.gradle.kts` are at the repo root, not inside an extra folder).
2. Open the **Actions** tab, choose **Build DUNE APK**, and wait for the green check (about 5–8 minutes the first time). You can also press **Run workflow**.
3. Open the finished run and download the **dune-debug-apk** artifact (a zip containing `app-debug.apk`).
4. Unzip it, copy the APK to your phone, and allow "Install unknown apps" for your file manager or browser.

Android Studio alternative: open this folder with JDK 17, sync Gradle, and choose **Build > Build APK(s)**.

## Important limitations before release
This is a starter project and has **not been compiled or tested on a physical device in this environment**. The sound effects are simple generated system tones, not the custom bundled OGG soundtrack from the roadmap. Settings persistence/toggles, notification thresholds, native package icon extraction, advanced shader effects, and complete onboarding still need work. The gyro parallax currently follows the available rotation-vector sensor and should be checked on target phones.

Google Play restricts `QUERY_ALL_PACKAGES`; confirm that your app qualifies and complete the Play permission declaration. Usage Access also needs clear user disclosure and Play policy review. No INTERNET permission, cloud sync, or analytics are included. Replace the placeholder app icon and add a privacy-policy URL before release.
