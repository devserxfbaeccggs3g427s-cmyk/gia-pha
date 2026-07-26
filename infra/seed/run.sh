#!/usr/bin/env bash
# Apply the import seed to a fresh infra.
# Dependency order (per design.md §Migration Strategy):
#   identity → tree → members → relationships → events → media-metadata
#           → sharing → reporting → audit
set -euo pipefail

if [[ ! -f infra/seed/export.json ]]; then
  echo "[seed] No export.json found. Generate one with the monolith export tool first."
  exit 0
fi

# In Phase 7.1 this becomes a real benchmark/seed driver. For now it just
# announces the intended order so the runbook is accurate.
SERVICES=(identity-service tree-service members-service relationships-service \
          events-service media-metadata-service sharing-service reporting-service \
          audit-service)
for s in "${SERVICES[@]}"; do
  echo "[seed] would apply seed for ${s}"
done
echo "[seed] Done (no-op until services are wired in later phases)."
