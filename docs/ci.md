# CI configuration notes

This document tracks the secrets and environment variables the CI pipeline
needs in order to produce signed release artifacts. The full CI workflow
itself lands in Phase 0 Task 4 (`.github/workflows/ci.yml`); Task 1.3 only
sets up the Gradle side of signing so that release builds **fail fast** when
a required secret is missing.

## Release signing

The release signing config in `app/build.gradle.kts` reads these environment
variables at configuration time:

- `KEYSTORE_BASE64` — base64-encoded keystore file (JKS or PKCS12).
- `KEYSTORE_PASSWORD` — keystore password.
- `KEY_ALIAS` — must be `aetheris-release` (see design §2.1).
- `KEY_PASSWORD` — key password.

If **any** of these is missing when a release-assembly task is invoked
(`assembleRelease`, `bundleRelease`, `assemble`, `build`), Gradle aborts
with:

```
Release signing env vars are missing. Set KEYSTORE_BASE64, KEYSTORE_PASSWORD,
KEY_ALIAS, KEY_PASSWORD. See docs/ci.md.
```

Local debug builds (`assembleDebug`, test tasks) continue to work with no
signing env vars set.

### Generating the release keystore locally

Use `keytool` from the JDK. The alias is fixed by the design:

```bash
keytool -genkey -v \
  -keystore aetheris-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias aetheris-release
```

Follow the prompts and record the keystore password, key password, and
distinguished-name fields in a safe place (e.g., the team password manager).
**Do not** commit the `.jks` file; it is listed in `.gitignore`.

### Base64-encoding the keystore for CI

GitHub Actions secrets are plain strings, so the keystore bytes are stored
as a single base64 blob:

```bash
# macOS
base64 -i aetheris-release.jks | tr -d '\n' | pbcopy

# Linux
base64 -w0 aetheris-release.jks
```

Paste the output into the repository secret named `KEYSTORE_BASE64`. Create
the other three secrets (`KEYSTORE_PASSWORD`, `KEY_ALIAS` = `aetheris-release`,
`KEY_PASSWORD`) alongside it.

At build time, `app/build.gradle.kts` decodes `KEYSTORE_BASE64` into
`app/build/release-keystore.jks` (gitignored) and wires the decoded file
into `signingConfigs.release`.

## Future CI secrets (added in Task 4.2)

The full CI pipeline will also need:

- `FIREBASE_APP_ID` — Firebase App Distribution target.
- `FIREBASE_CLI_TOKEN` — token for `firebase appdistribution:distribute`.

These are not consumed by Gradle directly and are not a prerequisite for
`assembleRelease`; see Task 4 for their setup.


## CI pipeline (`.github/workflows/ci.yml`)

The pipeline is a strict sequence of GitHub Actions jobs. Each job `needs`
the previous one so a failure short-circuits the rest of the run.

| Stage | Job name | Gradle invocation | Gating |
| --- | --- | --- | --- |
| 1 | `lint` | `./gradlew detekt lintDebug` | Hard |
| 2 | `manifest-check` | `./gradlew checkManifests` | Hard |
| 3 | `unit-test` | `./gradlew test` | Hard |
| 4 | `connectedAndroidTest` | `./gradlew pixel7proApi34GoogleApisDebugAndroidTest` | Soft (Phase 0) — hardened in Phase 2 Task 38 |
| 5 | `assembleRelease` | `./gradlew :app:assembleRelease :app:bundleRelease` | Hard |
| 6 | `uploadToFirebaseAppDistribution` | `./gradlew :app:appDistributionUploadRelease` | Soft, main-only — activates once Task 5 wires the plugin |

Triggers:

- `push` to `main` — runs all stages; stage 6 uploads to Firebase.
- `push` to tags matching `v*` — runs stages 1–5.
- `pull_request` to `main` — runs stages 1–5; stage 6 is skipped.
- `workflow_dispatch` — runs all stages.

Caching:

- `gradle/actions/setup-gradle@v4` caches Gradle user home and the
  configuration cache, keyed on `gradle/wrapper/gradle-wrapper.properties`
  and all `**/*.gradle*` + `**/gradle/libs.versions.toml` files.
- `android-actions/setup-android@v3` installs the SDK into the runner's
  tool cache so subsequent steps don't re-download platform / build-tools.

Concurrency:

- Pull-request runs on the same ref cancel earlier in-progress runs so a
  fresh push supersedes the stale run. `main` pushes never cancel.

### `connectedAndroidTest` — Phase 0 caveat

Gradle Managed Devices need nested virtualization (KVM) on the runner.
GitHub's hosted `ubuntu-latest` runners expose `/dev/kvm`, but the
combination of GMD + AGP 8.7 on hosted runners is still flaky enough that
Task 4 runs this stage with `continue-on-error: true`. The hardening pass
(likely a switch to Firebase Test Lab or a self-hosted runner) lands with
**Phase 2 Task 38** when macrobenchmark needs a stable device target.

### `uploadToFirebaseAppDistribution` — Phase 0 caveat

The Firebase App Distribution Gradle plugin is not yet applied — that's
**Phase 0 Task 5.2**. Until then the `:app:appDistributionUploadRelease`
task does not exist. The `distribute` job is therefore soft-gated:

1. It only runs on `push` events to `main`.
2. It `continue-on-error: true` so a missing task does not fail the run.
3. It skips itself cleanly when `FIREBASE_APP_ID` / `FIREBASE_CLI_TOKEN`
   are unset (the expected state pre-Task-5).

Once Task 5 lands, configure the two Firebase secrets and remove
`continue-on-error` from the `distribute` job.

## Required repository secrets

All secrets are configured under **Settings → Secrets and variables →
Actions** on the GitHub repository.

| Secret | Consumed by job | Purpose |
| --- | --- | --- |
| `KEYSTORE_BASE64` | `assembleRelease`, `distribute` | Base64-encoded release keystore. Materialized into `app/build/release-keystore.jks` at build time (Task 1.3). |
| `KEYSTORE_PASSWORD` | `assembleRelease`, `distribute` | Keystore password. |
| `KEY_ALIAS` | `assembleRelease`, `distribute` | Must equal `aetheris-release` per design §2.1; the pipeline enforces this literal. |
| `KEY_PASSWORD` | `assembleRelease`, `distribute` | Key password. |
| `FIREBASE_APP_ID` | `distribute` | Firebase project App ID (`1:<sender-id>:android:<app-hash>`). Added in Task 5.1. |
| `FIREBASE_CLI_TOKEN` | `distribute` | Output of `firebase login:ci`, used by the App Distribution plugin for unattended uploads. |

The `assembleRelease` job verifies the signing secrets explicitly before
invoking Gradle. A missing secret fails the stage with an annotated
`::error::` message; a `KEY_ALIAS` other than `aetheris-release` also
fails the stage.

## Build summary reporting

Every run posts a **Job Summary** (`$GITHUB_STEP_SUMMARY`) with the
information Task 4.3 calls out:

- **Test counts (unit + PBT).** The `unit-test` job uses
  [`mikepenz/action-junit-report@v5`][junit-action] to parse every
  `TEST-*.xml` under `**/build/test-results/**` and post a per-suite
  pass/fail table plus totals. jqwik results land in the same JUnit XML
  stream as plain JUnit tests, so they're included in the same counts.
- **Test counts (instrumented).** The `connectedAndroidTest` job reuses
  the same action against `**/build/outputs/androidTest-results/**`.
- **APK / AAB size.** The `assembleRelease` job walks
  `app/build/outputs`, prints a Markdown size table to the job summary,
  and fails the job if any artifact exceeds **500 MB** (a loose Phase 0
  guardrail; tightened in Phase 2 once a binary-size budget is set per
  design §12.1).
- **Distribute status.** The `distribute` job notes whether it uploaded
  or skipped (and why).

Artifacts uploaded for download from the run page:

- `lint-reports` — detekt + Android Lint HTML/XML.
- `junit-unit` — JUnit XML for all JVM tests.
- `junit-instrumented` — JUnit XML for GMD tests.
- `release-artifacts` — signed `.apk` and `.aab` from `assembleRelease`.

[junit-action]: https://github.com/mikepenz/action-junit-report
