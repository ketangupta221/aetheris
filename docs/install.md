<!--
  Screenshots pending Task 5.1 — add images to docs/assets/ when the first
  build is distributed through the Firebase project. Until then the flow is
  described in prose only.
-->

# Installing Aetheris Planner on your phone

Aetheris Planner is distributed through [Firebase App Distribution][fad]
during Phase 0–2 and through the Google Play Internal Testing track from
Phase 3 onward (see `docs/spec/design.md` §12.1). This document walks
through the Firebase install flow.

No USB cable is required at any step.

> 📎 **First-time CI configuration?** The Firebase project itself, the
> `FIREBASE_APP_ID` / `FIREBASE_CLI_TOKEN` GitHub Actions secrets, and
> the `internal-testers` group are set up once via the runbook at
> [`firebase-setup.md`](firebase-setup.md). This page covers the
> per-device install flow only.

[fad]: https://firebase.google.com/products/app-distribution

## Prerequisites (one-time)

1. You have been added to the `internal-testers` group on the Firebase
   project. The invite arrives by email from `noreply@firebase.google.com`
   with the subject "You have been invited to test Aetheris Planner".
2. Your Android phone is running Android 8.0 (API 26) or later. The
   reference device is a Samsung Galaxy S23 on Android 13+.

## First-time setup

1. Open the invite email on the phone and tap **"Get started"**.
   (If the link opens in a browser, tap the **"Install" / "Download"**
   button.)
2. Firebase will ask you to sign in with the Google account the invite
   was sent to.
3. You'll be redirected to the **Firebase App Tester** app on the Play
   Store. Install it. (The App Tester app is published by Google; it
   is a safe download.)
4. Open App Tester and sign in with the same Google account.
5. Aetheris Planner will appear in the app's list.

## Per-build: receive and install

Every time CI pushes a new build to Firebase:

1. You receive a notification from App Tester titled "Aetheris Planner —
   new build available". The notification includes the release notes
   (the last commit's subject line in Phase 0; `CHANGELOG.md`'s
   Unreleased section from Phase 1 onward).
2. Tap the notification. App Tester shows the build's details (version,
   release notes, size).
3. Tap **Download**. The APK downloads from Firebase's CDN.
4. After download, tap **Install**. Android shows the standard
   "Install unknown app" prompt; grant it to App Tester (one time only).
5. Aetheris Planner installs. Tap **Open** to launch.

## Troubleshooting

- **"App not installed"**: uninstall any previous debug build of
  Aetheris Planner first. Debug and release builds are signed with
  different keys and can't replace each other.
- **Notification never arrives**: open App Tester, pull to refresh
  the build list.
- **Sign-in loop**: make sure the Google account on your phone matches
  the one the invite was sent to.

## Wireless ADB alternative (developer-only)

For live debugging without USB, enable Wireless ADB (Android 11+):

1. Settings → Developer options → Wireless debugging → Pair device
   with pairing code.
2. On your computer:
   ```bash
   adb pair <phone-ip>:<port>   # paste the pairing code
   adb connect <phone-ip>:<port>
   adb install app-debug.apk
   adb logcat                    # live device logs over Wi-Fi
   ```

Wireless ADB is the only supported way to get `adb logcat` off the
device for this project — we deliberately do not ship any remote
logging.

## Upgrade path: Play Internal Testing (Phase 3+)

When the project reaches Phase 3 (LLM chat MVP, ~1 GB APK) we switch
the primary channel to the Google Play Internal Testing track because
Firebase App Distribution's tap-to-install UX degrades on cellular
once the APK passes ~500 MB. The invite flow through Play is similar
to Firebase App Distribution but delivered by the Google Play Store
itself. Details land in `docs/spec/design.md` §12.1 and in a Phase 3
update to this document.
