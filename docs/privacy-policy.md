
# Privacy Policy

**Last updated**: 2026-05-10

Aetheris Planner is designed so that **no personal data ever leaves
your device**. This policy describes what the app does with the
information you enter.

## Summary

- **Data collected by us: none.** We do not operate a server.
- **Data shared with third parties: none.**
- **Data transmitted over the network: none at runtime.** The only
  network activity associated with this app is (a) Google Play
  installing the app on your device, and (b) optional, one-time,
  user-consented downloads of larger AI model files that you
  explicitly approve inside the app.
- **Analytics, telemetry, crash reporting, or advertising SDKs:
  none.** The app's production build contains no such SDKs.


## Data you create

Every piece of data you enter (tasks, schedules, habits, chat history
with the on-device AI, focus-session logs, planning and shutdown
reflections, any iCalendar files you import) is:

- Stored only on your device.
- Stored in a SQLite database encrypted with SQLCipher (AES-256).
- Protected by a key generated on your device and kept in the Android
  Keystore. The key never leaves your device.

When you uninstall the app, Android deletes the encrypted database
along with the app.

## The on-device AI assistant

The chat assistant in Aetheris Planner uses a language model that
runs entirely on your device. Your chat messages and the surrounding
context (such as your local time and a compact summary of tasks
scheduled in the next seven days) are passed to the model running on
your device. None of this leaves your device.


## Consented model downloads

The app ships with a small language model and a small speech-
recognition model bundled inside the installer, so it works out of
the box with zero network. You may optionally download larger, more
capable models (for example Gemma 2 2B or Whisper base). When you do:

- You will see a consent dialog showing the asset name, its size in
  megabytes, the source URL, and a statement that only this asset
  will be fetched.
- You may decline, and the app continues to work with the bundled
  models.
- No personal data is transmitted as part of the download — only the
  outgoing HTTPS request for the model file.

You may revoke consent and delete any downloaded model at any time
from Settings → Models.

## Notifications and alarms

The app schedules local notifications and exact alarms for your
reminders. These are created and fired on your device by the Android
system — they do not go through any server.

## Backups

When you export an encrypted backup file, the export happens entirely
on your device. You choose where to save it. We never see it.


## Children's privacy

The app collects no data, so there is nothing specific to children's
privacy to declare. If you have concerns, please open a GitHub issue
at <https://github.com/ketangupta221/aetheris/issues>.

## Changes

If we ever change this policy (for example, because a future feature
requires network access), we will update this document and the
"Last updated" date above, and the app will prompt you to re-consent
before any new data flow is enabled. The strict-offline-by-default
promise in Requirement 5 of the design document guarantees this.

## Contact

Questions? Open an issue at
<https://github.com/ketangupta221/aetheris/issues>.
