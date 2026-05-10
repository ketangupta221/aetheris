# Aetheris Planner

> Offline AI Planner for your Daily Life

Aetheris Planner is a privacy-first Android productivity app. Everything
you create — tasks, schedules, focus sessions, reflections — lives only
on your device. No servers, no cloud, no analytics, no ads.

## Brand

**Aetheris** draws from the Latin *aether* — the classical fifth element,
pure and untouchable — plus **I**ntelligent **S**ystem. The name captures
the project's promise: on-device AI that never leaves the device.

## Status

Phase 0 (scaffolding). See [`.kiro/specs/ai-daily-planner/tasks.md`](.kiro/specs/ai-daily-planner/tasks.md)
for the full roadmap.

## Features (planned)

- Visual day-timeline planner (Structured-style)
- On-device LLM chat assistant (Gemma 3 1B bundled, Gemma 2 2B recommended
  download, larger models optional)
- On-device speech capture (whisper-tiny bundled, larger Whisper models
  optional)
- Tasks, habits, focus sessions, planning and shutdown rituals
- Quick-capture widget and Android Sharesheet integration
- ICS calendar import
- Encrypted SQLite storage (SQLCipher + Android Keystore)
- Encrypted backup and restore
- Material You theming

## Privacy

By design, Aetheris Planner:

- Stores every piece of user data only on your device in an encrypted
  SQLite database (SQLCipher, Android Keystore).
- Does **not** declare `android.permission.INTERNET` in the main
  application manifest. The only module that can perform outbound
  network I/O is `:distribution:model-downloader`, installed on-demand
  via Play Feature Delivery after explicit user consent. This is
  enforced at build time by a custom `:checkManifests` Gradle task and
  at runtime by a `SocketFactory` that throws `SecurityException` on
  any socket opened outside the consented-download scope.
- Contains no analytics, telemetry, crash reporting, or advertising SDKs.

See [`docs/privacy-policy.md`](docs/privacy-policy.md) for the formal
privacy policy.

## Local development

Requirements:

- JDK 17 (Temurin recommended)
- Android SDK with platform 34 installed
- Gradle 8.11 (via the wrapper — no system install required)

```bash
./gradlew projects           # list all modules
./gradlew checkManifests     # verify no INTERNET outside :distribution:model-downloader
./gradlew :app:assembleDebug
./gradlew :core:common:test
```

Runs on macOS, Linux, and Windows (WSL recommended on Windows).

## Architecture

Multi-module Gradle project. 20 modules — see `settings.gradle.kts` for
the full list. High-level layout:

- `:app` — entry point, navigation graph, Hilt root
- `:core:common`, `:core:domain` — pure JVM
- `:core:data`, `:core:scheduling`, `:core:notifications` — Android libraries
- `:feature:*` — UI features (timeline, tasks, chat, quickcapture, ...)
- `:ai:*` — AI stack (router, NL parser, entity resolver, LLM, speech)
- `:distribution:model-downloader` — the only module allowed to open
  outbound sockets, gated by user consent

Full design lives in [`.kiro/specs/ai-daily-planner/design.md`](.kiro/specs/ai-daily-planner/design.md).

## Testing

- JVM unit tests: `./gradlew test`
- Property-based tests (jqwik, 500 iters by default): same `test` task
- Instrumented tests on Gradle Managed Devices:
  `./gradlew pixel7proApi34GoogleApisDebugAndroidTest`
- Real-device performance: Firebase Test Lab matrix (Samsung S23 reference)

No USB tethering required at any point. See [`docs/install.md`](docs/install.md)
for the tap-to-install flow via Firebase App Distribution.

## License

PolyForm Noncommercial 1.0.0. See [`LICENSE`](LICENSE).

Source-available for personal, educational, research, and non-profit use.
Commercial use by third parties is prohibited. The author retains full
rights to their own builds and distributions.

Third-party component licenses are listed in [`NOTICE`](NOTICE).
