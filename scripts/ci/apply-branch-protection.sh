#!/usr/bin/env bash
# apply-branch-protection.sh
#
# Idempotently applies the main-branch protection policy for the
# Aetheris Planner GitHub repository. The authoritative payload lives
# in `scripts/ci/branch-protection.main.json`.
#
# Usage:
#     GITHUB_PAT=<personal-access-token> ./scripts/ci/apply-branch-protection.sh
#
# Optional environment overrides:
#     REPO_OWNER   default: ketangupta221
#     REPO_NAME    default: aetheris
#     BRANCH       default: main
#     PAYLOAD      default: scripts/ci/branch-protection.main.json
#
# The GitHub PAT must have `repo` scope (or, for a fine-grained token,
# `Administration: Read and write` on the target repository).
#
# This script is idempotent: GitHub's PUT
# /repos/{owner}/{repo}/branches/{branch}/protection endpoint is an
# upsert, so re-running has no effect when the config is already
# correct. If the remote config has drifted, it is restored to match
# the JSON payload verbatim.
#
# Reference: https://docs.github.com/en/rest/branches/branch-protection
set -euo pipefail

: "${GITHUB_PAT:?GITHUB_PAT must be set (GitHub PAT with repo admin scope)}"

REPO_OWNER="${REPO_OWNER:-ketangupta221}"
REPO_NAME="${REPO_NAME:-aetheris}"
BRANCH="${BRANCH:-main}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAYLOAD="${PAYLOAD:-${SCRIPT_DIR}/branch-protection.main.json}"

if [[ ! -f "${PAYLOAD}" ]]; then
  echo "error: payload file not found: ${PAYLOAD}" >&2
  exit 1
fi

API_URL="https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/branches/${BRANCH}/protection"

echo "Applying branch protection to ${REPO_OWNER}/${REPO_NAME}@${BRANCH}..."
echo "Payload: ${PAYLOAD}"

# -f makes curl exit non-zero on HTTP >= 400; -sS is quiet-with-errors.
# We write the response body to stdout so CI logs capture the applied
# config, but we deliberately never echo $GITHUB_PAT.
http_status=$(curl -sS -o /tmp/branch-protection-response.json -w '%{http_code}' \
  -X PUT \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  --data-binary "@${PAYLOAD}" \
  "${API_URL}")

if [[ "${http_status}" != "200" ]]; then
  echo "error: branch protection update failed (HTTP ${http_status})" >&2
  cat /tmp/branch-protection-response.json >&2 || true
  exit 1
fi

echo "OK (HTTP ${http_status}). Current protection config:"
cat /tmp/branch-protection-response.json
echo
