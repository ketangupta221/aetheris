# Repository metadata policy

The GitHub repository `ketangupta221/aetheris` carries three pieces of
user-visible metadata that power discoverability on GitHub search, the
repo sidebar, and topic pages: the **description**, the **homepage**
URL, and the **topics** list. This document is the authoritative
policy; the machine-applicable payload lives in
[`scripts/ci/repo-metadata.json`](../scripts/ci/repo-metadata.json)
and can be re-applied with
[`scripts/ci/apply-repo-metadata.sh`](../scripts/ci/apply-repo-metadata.sh).

_Requirements: 29.8 (see [`docs/spec/requirements.md`](spec/requirements.md))._

## Policy at a glance

| Field | Value |
|-------|-------|
| Description | `Offline, privacy-first Android productivity app with on-device LLM, tasks, habits, and focus — no cloud, no analytics.` (118 chars — under GitHub's 130-char search preview cap) |
| Homepage | `https://ketangupta221.github.io/aetheris/privacy-policy` |
| Topics (15 of 20 max) | `android`, `gemma`, `hilt`, `jetpack-compose`, `kotlin`, `llama-cpp`, `mediapipe`, `offline-first`, `on-device-ai`, `on-device-llm`, `privacy`, `productivity`, `room`, `sqlcipher`, `whisper-cpp` |

All topic slugs are lowercase and hyphenated per GitHub's
normalisation rules. GitHub caps repositories at 20 topics; we are
currently at 15, leaving headroom for future additions (e.g.
`material3`, `workmanager`, `datastore`) without forcing an eviction.

## Rationale

### Description

The one-liner compresses the project's three strongest differentiators
into the space GitHub shows above the README in search results and on
topic pages:

1. **Offline, privacy-first** — the primary hook; no servers or
   analytics, which matches Requirement 29.1.
2. **On-device LLM** — signals the technical novelty versus generic
   task/habit apps.
3. **Tasks, habits, and focus** — grounds the app in concrete
   productivity surfaces so the audience understands what ships.

The longer tagline _"Offline AI Planner for your Daily Life —
privacy-first Android productivity app with on-device LLM, tasks,
habits, and focus sessions. No servers, no cloud, no analytics."_ is
preserved in the README hero; the short form is used as the GitHub
description because anything past ~130 characters is truncated in
search cards.

### Homepage

The homepage URL is `https://ketangupta221.github.io/aetheris/privacy-policy`.
This is intentional for Phase 0: the GitHub Pages site root
(`https://ketangupta221.github.io/aetheris/`) currently returns **HTTP
404** because only the `/privacy-policy` document has been published
(see [`docs/published-urls.md`](published-urls.md) and
[`docs/privacy-policy.md`](privacy-policy.md)). Pointing the homepage
at a live 2xx URL matters because GitHub surfaces this link in the
sidebar and on social cards; a 404 there would undermine the
"privacy-first" claim we are making in the description.

Once a Pages landing page is published (tracked as a follow-up to task
8.7), update `scripts/ci/repo-metadata.json` to point `homepage` at
the Pages root and re-run `apply-repo-metadata.sh`.

### Topics

The topic list is organised into three groups, all of which improve
discoverability:

- **Platform / language** — `android`, `kotlin`, `jetpack-compose`,
  `hilt`. These are the terms a mobile developer browsing GitHub
  Topics will filter by.
- **Product positioning** — `offline-first`, `privacy`, `productivity`,
  `on-device-ai`, `on-device-llm`. Both `on-device-ai` and
  `on-device-llm` are kept because the GitHub Topics community uses
  them interchangeably and each has a distinct topic page we want to
  appear on.
- **Technical stack signal** — `llama-cpp`, `mediapipe`, `whisper-cpp`,
  `gemma`, `room`, `sqlcipher`. These identify the specific libraries
  and models the app ships with, which helps attract contributors who
  search for library-specific examples.

## How to re-apply

The script is idempotent (PATCH upserts description/homepage, PUT
replaces the topic list):

```bash
GITHUB_PAT=<your_pat> ./scripts/ci/apply-repo-metadata.sh
```

The PAT must have `repo` scope (or, for a fine-grained token,
`Administration: Read and write` on `ketangupta221/aetheris`). The
script never echoes the PAT.

## How to verify

```bash
# Description + homepage
curl -sS \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/ketangupta221/aetheris \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["description"]); print(d["homepage"])'

# Topics
curl -sS \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/ketangupta221/aetheris/topics
```

The returned values should match `scripts/ci/repo-metadata.json`
verbatim.

## Change log

- **2025-05-10** — Initial policy codified and applied to
  `ketangupta221/aetheris`. Remote state after apply:
  - `description`: `Offline, privacy-first Android productivity app with on-device LLM, tasks, habits, and focus — no cloud, no analytics.`
  - `homepage`: `https://ketangupta221.github.io/aetheris/privacy-policy` (Pages root currently returns HTTP 404; privacy-policy returns HTTP 200).
  - `topics`: `android`, `gemma`, `hilt`, `jetpack-compose`, `kotlin`, `llama-cpp`, `mediapipe`, `offline-first`, `on-device-ai`, `on-device-llm`, `privacy`, `productivity`, `room`, `sqlcipher`, `whisper-cpp`.
