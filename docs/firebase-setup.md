# Firebase App Distribution — one-time setup runbook

This runbook covers the **one-time** Firebase project creation and
GitHub Actions secret wiring that Phase 0 Task 5.1 depends on. Once
complete, every `push` to `main` uploads a signed APK to the
`internal-testers` group and the user receives a tap-install link on
their phone (see [`install.md`](install.md)).

The runbook is split into three sections:

1. [Firebase Console](#1-firebase-console-create-the-project)
2. [Local machine — generate a CI token](#2-local-machine--generate-a-ci-token)
3. [GitHub — add repository secrets](#3-github--add-repository-secrets)

A [verification checklist](#4-verification-checklist) at the bottom
lets you confirm everything is wired correctly without exposing any
secret values.

> ⚠️ **Do not paste any of the generated values (`FIREBASE_APP_ID`,
> `FIREBASE_CLI_TOKEN`, keystore password, etc.) into chat, into an
> issue, or into any file in this repo.** They go directly into
> GitHub's secret storage and nowhere else.

## Why no `google-services.json`?

Aetheris Planner does **not** depend on the Firebase Android SDK at
runtime. We use Firebase only as a CI-driven distribution channel
through the [`com.google.firebase.appdistribution`][plugin] Gradle
plugin, which uploads the signed APK via a CLI refresh token — no
client-side Firebase initialization, no `google-services` plugin, no
`google-services.json`.

This matches Requirement 5.7 (no Analytics / Crashlytics / Messaging
SDKs) and Property 18 (zero network at runtime): the app itself never
talks to Firebase. Only the GitHub Actions runner does.

[plugin]: https://firebase.google.com/docs/app-distribution/android/distribute-gradle

---

## 1. Firebase Console — create the project

All of these steps happen in a browser under your Google account.

### 1.1 Create the Firebase project

1. Go to <https://console.firebase.google.com> and sign in with the
   Google account you want to own the project. This is the account
   that will receive the `internal-testers` invitation email.
2. Click **Add project** (or **Create a project** on first use).
3. Project name: **Aetheris Planner**. (The project ID is auto-derived,
   e.g. `aetheris-planner-<suffix>` — the suffix does not matter.)
4. When asked about **Google Analytics**, select **"Disable Google
   Analytics for this project"**. Per Requirement 5.7 this app must
   ship with no Analytics, Crashlytics, or Messaging SDKs; keeping
   Analytics off at the project level prevents anyone from
   accidentally wiring it in later.
5. Click **Create project**, then **Continue** when provisioning
   finishes.

### 1.2 Register the Android app

1. On the project's overview page, click the Android icon (**</>**
   may also appear — pick the green Android robot) under *"Get
   started by adding Firebase to your app"*.
2. Fill in the form:
   - **Android package name**: `dev.aetheris.planner`  (must match
     exactly — this is the `applicationId` set in
     `app/build.gradle.kts`).
   - **App nickname** (optional): *Aetheris Planner (Phase 0)*.
   - **Debug signing certificate SHA-1**: leave **blank**. App
     Distribution does not require it; it's only used for Firebase
     services (Auth, Dynamic Links) that we don't use.
3. Click **Register app**.
4. On the next step, **"Download `google-services.json`"**, click
   **Next** *without* downloading. The file is only needed by the
   Firebase SDK, which we don't include. If the Console forces the
   download, save it somewhere outside this repo — do **not** commit
   it.
5. Click **Next** through the remaining SDK integration steps. We
   never apply the `com.google.gms.google-services` plugin or any
   Firebase runtime SDK, so none of these steps apply to our build.
6. Click **Continue to console**.

### 1.3 Locate the App ID

This is the value that becomes the `FIREBASE_APP_ID` GitHub secret.

1. From the project overview, click the gear icon (top-left, next
   to **Project Overview**) → **Project settings**.
2. Scroll to **Your apps** → select the Android app you just
   registered.
3. Copy the **App ID** value. It looks like:

   ```
   1:1234567890:android:abcdef0123456789
   ```

   Format: `1:<sender-id>:android:<app-hash>`. The regex
   `^1:\d+:android:[a-f0-9]+$` should match.
4. **Save this value directly into GitHub Actions secrets** in
   section [3.1](#31-add-firebase_app_id). Do not paste it into
   chat, commits, or any file in this repo.

### 1.4 Create the `internal-testers` distribution group

The CI workflow uploads to a Firebase App Distribution group named
`internal-testers`. Create it now so the first CI upload has a
target to land in.

1. In the Firebase Console, left sidebar → **App Distribution**.
2. If prompted with *"Get started with App Distribution"*, click
   **Get started**.
3. Click the **Testers & Groups** tab.
4. Click **Add group**. Group name: `internal-testers` (lowercase,
   hyphen — this literal is in `app/build.gradle.kts`).
5. Click the group, then **Add testers**, and enter your own Google
   account email. That's the email that will receive the tap-install
   invite on your Samsung Galaxy S23.

---

## 2. Local machine — generate a CI token

The App Distribution Gradle plugin needs credentials to upload
builds. We use a **refresh token** produced by `firebase login:ci`,
which is the standard pattern for unattended CI uploads.

### 2.1 Install `firebase-tools`

```bash
# Requires Node.js 18+ (Homebrew: `brew install node`).
npm install -g firebase-tools

# Verify.
firebase --version        # prints e.g. "13.x.x"
```

### 2.2 Run `firebase login:ci`

```bash
firebase login:ci
```

This opens a browser. Sign in with the **same Google account** that
owns the Firebase project from section 1.1. After you click
**Allow**, the terminal prints a refresh token of the form:

```
✔  Success! Use this token to login on a CI server:

1//0abc...DEF

Example: firebase deploy --token "$FIREBASE_TOKEN"
```

Copy the token verbatim (the long `1//0...` string). This is the
value that becomes the `FIREBASE_CLI_TOKEN` GitHub secret.

> ⚠️ **Treat this token like a password.** It is a long-lived
> credential that can upload builds to your Firebase project. Do
> **not** paste it into chat, commits, email, or any file in this
> repo. Paste it directly into GitHub Actions secrets in
> section [3.2](#32-add-firebase_cli_token), then close the
> terminal (or run `history -c` / `clear` so the token is not left
> in your shell history).

### 2.3 (Optional) Sanity-check the token locally

You can confirm the token works without uploading anything by
listing the testers in the `internal-testers` group:

```bash
firebase appdistribution:testers:list \
  --project <PROJECT_ID> \
  --token "<FIREBASE_CLI_TOKEN>"
```

Replace `<PROJECT_ID>` with the project ID from
**Project settings → General → Project ID** (e.g.
`aetheris-planner-<suffix>`), and `<FIREBASE_CLI_TOKEN>` with the
token from section 2.2. A successful run prints a list (possibly
just your own email) with exit code `0`. Any auth failure produces a
non-zero exit code and a clear error message.

Do not commit this command with the token substituted in.

---

## 3. GitHub — add repository secrets

The CI workflow at `.github/workflows/ci.yml` reads two secrets in
the `distribute` job: `FIREBASE_APP_ID` and `FIREBASE_CLI_TOKEN`.
Add both under the repository's Actions secrets settings.

Direct link:

> <https://github.com/ketangupta221/aetheris/settings/secrets/actions>

Requires admin or write access to the repository.

### 3.1 Add `FIREBASE_APP_ID`

1. Open the URL above.
2. Click **New repository secret**.
3. **Name**: `FIREBASE_APP_ID`
4. **Secret**: paste the App ID from section
   [1.3](#13-locate-the-app-id). It should match the regex
   `^1:\d+:android:[a-f0-9]+$`.
5. Click **Add secret**.

### 3.2 Add `FIREBASE_CLI_TOKEN`

1. Click **New repository secret** again.
2. **Name**: `FIREBASE_CLI_TOKEN`
3. **Secret**: paste the token from section
   [2.2](#22-run-firebase-loginci). It starts with `1//0`.
4. Click **Add secret**.

Both secrets now appear in the secrets list with their values masked
(shown as `***`). GitHub does not let you read a secret back after
creation — that's by design. If you lose the values, revoke the
token (`firebase logout --token "<token>"`) and repeat the
respective section.

### 3.3 Confirm the other required secrets exist

The `distribute` job also needs the release-signing secrets that
Task 1.3 introduced. Confirm the following four are already present
(they were configured when Task 1.3 landed — see `docs/ci.md`):

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS` (must equal `aetheris-release`)
- `KEY_PASSWORD`

If any is missing, follow `docs/ci.md` to add it before triggering
a distribute run. The `assembleRelease` job verifies all four
explicitly and fails fast with an `::error::` annotation if any are
absent.

---

## 4. Verification checklist

Run through these before declaring Task 5.1 done end-to-end.

- [ ] **Firebase project exists** at
      <https://console.firebase.google.com>, named *Aetheris
      Planner*, with Analytics disabled.
- [ ] **Android app registered** under the project with package name
      exactly `dev.aetheris.planner`.
- [ ] **App ID captured** in the GitHub secret `FIREBASE_APP_ID` and
      matches the format `1:\d+:android:[a-f0-9]+` (verify shape
      only; **do not echo the value**).
- [ ] **Distribution group `internal-testers` exists** under
      Firebase App Distribution → Testers & Groups, with your own
      Google account email as a tester.
- [ ] **`firebase-tools` installed locally** and
      `firebase login:ci` produced a token that was pasted into the
      GitHub secret `FIREBASE_CLI_TOKEN`. Optional dry-run:
      `firebase appdistribution:testers:list --project <PROJECT_ID>
      --token "<FIREBASE_CLI_TOKEN>"` exits `0`.
- [ ] **GitHub Actions secrets listed** at
      <https://github.com/ketangupta221/aetheris/settings/secrets/actions>
      include all six of:
      - `KEYSTORE_BASE64`
      - `KEYSTORE_PASSWORD`
      - `KEY_ALIAS`
      - `KEY_PASSWORD`
      - `FIREBASE_APP_ID`
      - `FIREBASE_CLI_TOKEN`
- [ ] **First end-to-end upload succeeded.** Push a trivial commit
      (or re-run the latest workflow) on `main`. The `distribute`
      job in the resulting CI run should complete without the
      *"Firebase secrets not set; skipping upload"* log line, and
      a build should appear in Firebase Console → App Distribution
      → Releases.
- [ ] **Tap-install invite received** on the tester email. Follow
      [`install.md`](install.md) on your phone to download and
      install the APK.

Once every item is checked, Task 5.1 is complete end-to-end. Update
`docs/spec/tasks.md` (the `5.1` line) from `[-]` to `[x]`.

---

## Rotating / revoking the CI token

If the token is ever exposed:

1. Revoke it locally:
   ```bash
   firebase logout --token "<FIREBASE_CLI_TOKEN>"
   ```
2. Regenerate it by repeating section
   [2.2](#22-run-firebase-loginci).
3. Update the GitHub secret by deleting `FIREBASE_CLI_TOKEN` and
   re-creating it with the new value (there is no in-place update
   — that is intentional).
