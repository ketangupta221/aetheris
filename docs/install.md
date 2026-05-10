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

> 📸 **Screenshots — please capture on first real install.** When you
> run through this flow for the first time on the Samsung Galaxy S23,
> take the four screenshots listed in
> [§ Screenshots to capture](#screenshots-to-capture) below and drop
> them into `docs/assets/`. This page references them by path so they
> render inline once present; until then the prose is authoritative.

[fad]: https://firebase.google.com/products/app-distribution

## What Phase 0 actually gives you

Before installing, set expectations: the Phase 0 build (`v0.0-scaffold`)
is a runnable **scaffold**, not a product. On launch you will see:

- A one-screen onboarding stub with a **Continue** button.
- A blank Timeline placeholder after you tap Continue.

There are **no working tasks, chat, speech, habits, or focus-session
features** in `v0.0-scaffold`. Features come online progressively:
`v0.1-mvp` adds tasks + NL-only chat (Phase 1), `v0.2-polish` adds
recurrence polish + search (Phase 2), `v0.3-llm-mvp` adds on-device
LLM chat (Phase 3), and so on. See
[`docs/spec/tasks.md`](spec/tasks.md) for the full phased roadmap.

If what you see on first install looks empty, that is expected — it
confirms the scaffold launches and the install pipeline works
end-to-end.

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

![Firebase invite email][img-invite]
<!-- docs/assets/install-email-invite.png — pending capture -->

![App Tester build list showing Aetheris Planner][img-build-list]
<!-- docs/assets/install-app-tester-build-list.png — pending capture -->

## Per-build: receive and install

Every time CI pushes a new build to Firebase, you'll go through the
same short loop. What to expect at each step:

| # | What you do | What you see | Rough timing |
|---|---|---|---|
| 1 | CI finishes a push to `main`. | Notification from Firebase App Tester titled **"Aetheris Planner — new build available"**. | ~2–5 min from the `main` push (Gradle `assembleRelease` + Firebase upload). |
| 2 | Tap the notification. | App Tester opens to the build details screen showing version, size, and release notes (Phase 0: the last commit's subject line; Phase 1+: the `CHANGELOG.md` Unreleased section). | Instant. |
| 3 | Tap **Download**. | Progress bar as the APK streams from Firebase's CDN. | ~33 MB for the current Phase 0 debug APK; a signed release APK is a similar size in Phase 0 and will shrink modestly once R8 shrinking is enabled in a later phase. Expect a few seconds on Wi-Fi, slightly longer on cellular. |
| 4 | Tap **Install**. | Android shows the standard **"Install unknown app"** permission prompt for App Tester (one time only). Grant it, then the system installer takes over. | A few seconds. |
| 5 | Tap **Open** (or launch from the app drawer). | Aetheris Planner launches to the onboarding stub described in [§ What Phase 0 actually gives you](#what-phase-0-actually-gives-you). | Instant. |

![The "Install unknown apps" permission prompt][img-unknown-apps]
<!-- docs/assets/install-unknown-apps-prompt.png — pending capture -->

![Aetheris Planner launched on the device][img-launched]
<!-- docs/assets/install-launched-app.png — pending capture -->

## Privacy: what the network actually does

The app itself **does not open any sockets at runtime** (design §6
Property 18 — *Zero network at runtime*). On your phone, you will
never see a network-permission prompt inside Aetheris Planner during
Phase 0–2, because the app never asks for one.

The **only** outbound network traffic on your phone that has anything
to do with Aetheris Planner is the Firebase App Tester app (published
by Google, a Google Play Services component) downloading the APK from
Firebase's CDN in step 3 above. That is Google Play Services activity
— not Aetheris Planner's. Once the APK is installed, no further
network activity is associated with this app unless and until a
future phase adds an explicitly consented model download (see
[`docs/privacy-policy.md`](privacy-policy.md)).

See also the **Privacy** section of [`README.md`](../README.md) for
the one-paragraph summary, and
[`docs/privacy-policy.md`](privacy-policy.md) for the formal policy.

## Troubleshooting

- **"App not installed"**: uninstall any previous debug build of
  Aetheris Planner first. Debug and release builds are signed with
  different keys and can't replace each other.
- **Notification never arrives**: open App Tester, pull to refresh
  the build list.
- **Sign-in loop**: make sure the Google account on your phone matches
  the one the invite was sent to.

### Samsung Galaxy S23 specifics

The flow above is written generically, but the S23 on Android 13+ has
a few vendor specifics worth knowing:

- **"Install unknown apps" path.** On Samsung One UI the permission
  lives at **Settings → Apps → Firebase App Tester → Install unknown
  apps → Allow from this source**. The first-install prompt deep-links
  here automatically; after that you can manage it from the same path.
- **Knox scan notice (first install).** Samsung Knox may show a
  one-time **"Unknown app"** scan notice the first time you install an
  APK from a non-Play source. This is a security information prompt,
  not an error — let it finish and proceed with the install.
- **Auto Blocker.** Samsung's **Auto Blocker** feature (Settings →
  Security and privacy → Auto Blocker) can silently block APK
  installs from non-Play sources when enabled. If step 4 of the
  per-build flow above fails silently or with an unhelpful error,
  check whether Auto Blocker is on and either turn it off temporarily
  for this install or allowlist Firebase App Tester. Re-enable it
  afterwards if you want the protection back.

Claims above are conservative and may vary with One UI / Android
patch level; if your device shows something different, capture a
screenshot and update this section.

## Uninstalling Aetheris Planner

To uninstall: long-press the Aetheris Planner icon in the app drawer,
then tap **Uninstall** (or use Settings → Apps → Aetheris Planner →
Uninstall).

**All app data is deleted.** Aetheris Planner stores everything in an
app-private, SQLCipher-encrypted SQLite database (design §3.3) with
`android:allowBackup="false"` set on the manifest (design §10.4), so
Android neither backs the data up to Google Drive nor preserves it
across uninstall. This is intentional: the app has no cloud, so
**uninstall is a full data wipe**. If you want to keep your data,
export an encrypted backup first (available from Phase 1 onward —
see `docs/spec/design.md` §4).

Uninstalling App Tester afterwards is optional; it's just a generic
Google tool for Firebase App Distribution and does not store any
Aetheris Planner data.

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

## Screenshots to capture

On your first real install, please capture the following four
screenshots and save them into `docs/assets/` with the exact
filenames below. The image references earlier in this page will then
render inline automatically. Until the files exist, Markdown renders
the alt text — the prose above remains authoritative.

| File | When to capture |
|---|---|
| `docs/assets/install-email-invite.png` | The Firebase invite email open on the phone, showing the "Get started" button. |
| `docs/assets/install-app-tester-build-list.png` | The Firebase App Tester app showing Aetheris Planner in its build list. |
| `docs/assets/install-unknown-apps-prompt.png` | The Android "Install unknown apps" permission prompt for Firebase App Tester. |
| `docs/assets/install-launched-app.png` | Aetheris Planner launched on the device — the onboarding stub or Timeline placeholder is fine. |

Crop to the relevant screen area and avoid capturing any personal
notifications in the status bar.

[img-invite]: assets/install-email-invite.png "Firebase invite email (pending capture)"
[img-build-list]: assets/install-app-tester-build-list.png "App Tester build list (pending capture)"
[img-unknown-apps]: assets/install-unknown-apps-prompt.png "Install unknown apps prompt (pending capture)"
[img-launched]: assets/install-launched-app.png "Aetheris Planner launched (pending capture)"
