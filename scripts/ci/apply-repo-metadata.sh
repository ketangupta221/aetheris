#!/usr/bin/env bash
# apply-repo-metadata.sh
#
# Idempotently applies the authoritative repository metadata
# (description, homepage, topics) for the Aetheris Planner GitHub
# repository. The authoritative payload lives in
# `scripts/ci/repo-metadata.json`.
#
# Usage:
#     GITHUB_PAT=<personal-access-token> ./scripts/ci/apply-repo-metadata.sh
#
# Optional environment overrides:
#     REPO_OWNER   default: ketangupta221
#     REPO_NAME    default: aetheris
#     PAYLOAD      default: scripts/ci/repo-metadata.json
#
# The GitHub PAT must have `repo` scope (or, for a fine-grained token,
# `Administration: Read and write` on the target repository).
#
# This script is idempotent:
#   * PATCH /repos/{owner}/{repo} is an upsert — re-running when the
#     description/homepage already match is a no-op at the API level.
#   * PUT /repos/{owner}/{repo}/topics replaces the topic list with
#     exactly the set supplied in the payload, so drift is corrected.
#
# The PAT is never echoed. We write response bodies to /tmp for CI log
# capture but keep the Authorization header out of stdout/stderr.
#
# References:
#   https://docs.github.com/en/rest/repos/repos#update-a-repository
#   https://docs.github.com/en/rest/repos/repos#replace-all-repository-topics
set -euo pipefail

: "${GITHUB_PAT:?GITHUB_PAT must be set (GitHub PAT with repo admin scope)}"

REPO_OWNER="${REPO_OWNER:-ketangupta221}"
REPO_NAME="${REPO_NAME:-aetheris}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAYLOAD="${PAYLOAD:-${SCRIPT_DIR}/repo-metadata.json}"

if [[ ! -f "${PAYLOAD}" ]]; then
  echo "error: payload file not found: ${PAYLOAD}" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "error: python3 is required to parse ${PAYLOAD}" >&2
  exit 1
fi

REPO_API="https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}"
TOPICS_API="${REPO_API}/topics"

# Extract the description/homepage payload and topics payload as two
# separate compact JSON strings. Topics require their own endpoint
# with a `{"names":[...]}` envelope.
description_homepage_payload="$(python3 -c '
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
out = {"description": data["description"], "homepage": data["homepage"]}
sys.stdout.write(json.dumps(out))
' "${PAYLOAD}")"

topics_payload="$(python3 -c '
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
topics = data["topics"]
if not isinstance(topics, list) or not all(isinstance(t, str) for t in topics):
    raise SystemExit("topics must be an array of strings")
if len(topics) > 20:
    raise SystemExit(f"GitHub allows at most 20 topics; got {len(topics)}")
for t in topics:
    if t != t.lower() or "_" in t or " " in t:
        raise SystemExit(f"invalid topic slug: {t!r} (must be lowercase, hyphenated)")
sys.stdout.write(json.dumps({"names": topics}))
' "${PAYLOAD}")"

echo "Applying repo metadata to ${REPO_OWNER}/${REPO_NAME}..."
echo "Payload: ${PAYLOAD}"

# --- 1. Update description + homepage ---------------------------------
http_status=$(curl -sS -o /tmp/repo-metadata-response.json -w '%{http_code}' \
  -X PATCH \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  --data-binary "${description_homepage_payload}" \
  "${REPO_API}")

if [[ "${http_status}" != "200" ]]; then
  echo "error: repo metadata update failed (HTTP ${http_status})" >&2
  cat /tmp/repo-metadata-response.json >&2 || true
  exit 1
fi

echo "OK: description & homepage updated (HTTP ${http_status})."

# --- 2. Replace topics -------------------------------------------------
http_status=$(curl -sS -o /tmp/repo-topics-response.json -w '%{http_code}' \
  -X PUT \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  --data-binary "${topics_payload}" \
  "${TOPICS_API}")

if [[ "${http_status}" != "200" ]]; then
  echo "error: repo topics update failed (HTTP ${http_status})" >&2
  cat /tmp/repo-topics-response.json >&2 || true
  exit 1
fi

echo "OK: topics replaced (HTTP ${http_status})."
echo "Current topics:"
cat /tmp/repo-topics-response.json
echo
