#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# packaging script — produces a transferable archive of the current
# monorepo state for handoff to a new Kilo session.
#
# Output:
#   /tmp/gia-pha-handoff-<timestamp>.tar.gz
#
# Excludes: .git, node_modules, .next, target, *.class, .idea, .kilo/plans
# (those are environment-specific / bulky / regenerated automatically).
# ---------------------------------------------------------------------------
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TS="$(date +%Y%m%dT%H%M%S)"
OUT="/tmp/gia-pha-handoff-${TS}.tar.gz"
TMP="$(mktemp -d)"

echo "==> Packaging repo at ${REPO_ROOT}"
echo "==> Output: ${OUT}"

tar -czf "${OUT}" \
  --exclude='.git' \
  --exclude='node_modules' \
  --exclude='.next' \
  --exclude='target' \
  --exclude='*.class' \
  --exclude='.idea' \
  --exclude='.kilo/plans' \
  --exclude='.kilo/agent' \
  --exclude='.kilo/command' \
  -C "${REPO_ROOT}/.." \
  "$(basename "${REPO_ROOT}")"

SIZE=$(du -h "${OUT}" | cut -f1)
echo "==> Done: ${OUT} (${SIZE})"
echo "==> SHA256:"
shasum -a 256 "${OUT}" | awk '{print "    "$1}'
echo
BASENAME="$(basename "${OUT}")"
echo "==> To restore in a new session:"
echo "    tar -xzf ${BASENAME} -C /path/to/parent/"
echo "    cd gia-pha"
echo "    cat .kiro/specs/spring-boot-backend-migration/governance/SESSION-HANDOFF.md"