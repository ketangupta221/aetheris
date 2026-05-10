# Security Policy

## Reporting a vulnerability

If you find a security issue, please open a private security advisory
through GitHub (Security → Advisories → "Report a vulnerability") or
email `<ADD_YOUR_EMAIL_HERE>`. Do not open a public issue.

Please include:

- A description of the vulnerability.
- Steps to reproduce.
- Potential impact.
- Any suggested remediation.

We will acknowledge receipt within 72 hours and aim to respond with a
plan (fix, workaround, or "not a vulnerability") within 14 days.

## Scope

Aetheris Planner handles all user data on-device under SQLCipher-
encrypted storage with keys held in the Android Keystore. The runtime
zero-network invariant is enforced at build time by the `:checkManifests`
Gradle task and at runtime by the `NetworkDenyingSocketFactory` gate.
Any finding that defeats these invariants is in scope — please include
it in your report.

Also in scope:

- Unauthorised disclosure of user data from the encrypted database.
- Bypasses of the consent flow for `:distribution:model-downloader`.
- Privilege escalation in notification or alarm handlers.

## Out of scope

- Social-engineering attacks against individual contributors.
- Denial of service via intentional resource exhaustion on the user's
  own device.
- Vulnerabilities in underlying third-party libraries (please report
  those upstream; we will track updates via `NOTICE`).
