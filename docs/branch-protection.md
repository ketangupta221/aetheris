# Branch protection policy: `main`

`main` is the only long-lived branch in this repository. It is
protected on GitHub so that every change lands via a pull request that
passes CI and receives a human review. This document describes the
policy; the authoritative, machine-applicable payload lives in
[`scripts/ci/branch-protection.main.json`](../scripts/ci/branch-protection.main.json)
and can be re-applied with
[`scripts/ci/apply-branch-protection.sh`](../scripts/ci/apply-branch-protection.sh).

_Requirements: 29.8 (see [`docs/spec/requirements.md`](spec/requirements.md))._

## Policy at a glance

| Rule | Value | Notes |
|------|-------|-------|
| Required approving reviews | 1 | Any PR into `main` needs one approval. |
| Dismiss stale reviews | true | Re-approval is required after new commits. |
| Require code-owner review | false | No `CODEOWNERS` file yet (Phase 0). |
| Require last push approval | false | Author may push and still have the prior approval count. |
| Required status checks | `lint`, `manifest-check`, `unit-test`, `assembleRelease` | Sourced from [`.github/workflows/ci.yml`](../.github/workflows/ci.yml). All four must be green. |
| `strict` status checks | true | Branch must be up to date with `main` before merge. |
| Allow force pushes | false | Shared history is sacrosanct (see `amazon-builder-git`). |
| Allow deletions | false | `main` cannot be deleted. |
| Required conversation resolution | true | All PR conversations must be resolved before merge. |
| Required linear history | false | Merge commits are allowed. |
| Block creations | false | Not relevant for `main`. |
| Enforce for admins | **false** | Intentional for Phase 0 — see below. |
| Required signatures | false | May be tightened in a later phase. |

## Required status checks

The four contexts below are produced by the top-level CI workflow at
[`.github/workflows/ci.yml`](../.github/workflows/ci.yml). Their job
names in that workflow file must exactly match the list above, or the
merge queue will block on a check that is never reported.

- `lint` — Detekt + Android Lint.
- `manifest-check` — the `:buildSrc:checkManifests` Gradle task that
  enforces the manifest permission invariant (`INTERNET` confined to
  `:distribution:model-downloader`). See
  [`docs/spec/tasks.md`](spec/tasks.md) tasks 2.1–2.4.
- `unit-test` — JVM unit and property-based tests (`./gradlew test`).
- `assembleRelease` — produces the signed release APK. This also
  proves the release-signing secrets are wired up correctly.

If any of these jobs is renamed in the workflow, the JSON payload and
this document must be updated in the same PR, otherwise merges into
`main` will silently be allowed without the renamed check running.

## Admin bypass (Phase 0 only)

`enforce_admins` is **intentionally `false`** during Phase 0 so that
the repository owner can push directly to `main` when bootstrapping
scaffolding work. This is a deliberate tradeoff: it keeps early
infrastructure work fast while CI is still being shaken out, and it
lets Kiro-driven automation complete end-to-end runs without waiting
for an external reviewer that does not yet exist.

Once Phase 0 exits (task 9 in [`docs/spec/tasks.md`](spec/tasks.md)),
the expectation is:

1. Non-admin contributors must open pull requests for every change.
2. The admin (current repo owner) continues to have bypass privileges
   but switches to the PR flow by default, using the bypass only for
   emergency fixes or repo-configuration changes.
3. A later task will flip `enforce_admins` to `true` and add a
   `CODEOWNERS` file.

Until then, every automated or manual commit landing directly on
`main` from the admin account implicitly accepts the risk that CI has
not yet run on the final merged state.

## How to re-apply

The script is idempotent:

```bash
GITHUB_PAT=<your_pat> ./scripts/ci/apply-branch-protection.sh
```

The PAT must have `repo` scope (or, for a fine-grained token,
`Administration: Read and write` on `ketangupta221/aetheris`).

## How to verify

```bash
curl -sS \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  https://api.github.com/repos/ketangupta221/aetheris/branches/main/protection
```

The returned JSON should match the payload in
`scripts/ci/branch-protection.main.json`, modulo GitHub-supplied fields
(`url`, `contexts_url`, `checks[*].app_id`, `required_signatures`,
`lock_branch`, `allow_fork_syncing`).

## Change log

- **2025-05-10** — Initial policy codified. Verified remote config
  against `scripts/ci/branch-protection.main.json` on
  `ketangupta221/aetheris@main`: all fields match.
