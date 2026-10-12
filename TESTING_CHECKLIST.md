# DUNE pre-release test checklist

## Install and first run
- [ ] Build debug APK with JDK 17 / Android SDK 35; install on an Android 8.0+ phone.
- [ ] Verify portrait lock and edge-to-edge layout on a small and large phone.
- [ ] Fresh install: onboarding appears at the right time; dismissing it persists.
- [ ] Deny Usage Access and confirm Preview mode works without real app records.
- [ ] Grant Usage Access, return to DUNE, and verify app list refreshes.

## App discovery and persistence
- [ ] Verify native app icons load and missing/unavailable icons fall back to initials.
- [ ] Search by label and package name; clear search; check 30/60/90-day filters.
- [ ] Check metal detector shortlist reflects the longest inactivity estimates.
- [ ] Swipe-clean an app, close/reopen the dialog, and verify progress persists.
- [ ] Rescue an app and confirm it launches; verify rescue count/state persists.
- [ ] Bury an app, verify it is hidden from the main list, then unbury it from the archive.
- [ ] Confirm bury never opens Android's uninstall flow and does not uninstall the app.

## Settings and background behavior
- [ ] Toggle ambience; confirm bundled wind loops only while enabled and stops when disabled.
- [ ] Toggle haptics and confirm the setting is respected.
- [ ] Enable reminders on Android 13+ and grant notification permission; test a qualifying record or a test build with an adjusted threshold.
- [ ] Change reminder threshold and verify settings persist after force-stop/restart.
- [ ] Confirm reminders are not sent when the setting or OS permission is off.
- [ ] Verify daily WorkManager refresh is best-effort and does not reset archive/rescue/wipe state.

## Performance and store policy
- [ ] Test on a phone with and without a rotation-vector sensor; check parallax remains subtle.
- [ ] Swipe rapidly and verify particle count stays capped and UI remains responsive.
- [ ] Check battery/audio behavior when app goes to background and after process recreation.
- [ ] Review `QUERY_ALL_PACKAGES` eligibility and complete the Play Console declaration if retained.
- [ ] Publish a hosted privacy-policy URL and confirm it matches the release binary.
- [ ] Test release build, accessibility/contrast, TalkBack labels, crash behavior, and Android 8–15 devices.
