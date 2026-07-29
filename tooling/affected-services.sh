#!/usr/bin/env bash
# affected-services.sh — enumerate changed services for the changed-service
# pipeline. Used by `platform/ci/ci.yml` to build/test only the affected
# services. Without this, the root would have to build all services in one
# reactor build, which ADR-011 forbids.

set -euo pipefail

BASE_REF=${BASE_REF:-origin/main}
ROOT=${ROOT:-.}

mapfile -t services < <(find "$ROOT/services" -mindepth 2 -maxdepth 2 -name pom.xml -printf '%h\n' | sort)

changed=()
for svc in "${services[@]}"; do
  rel="${svc#$ROOT/}"
  if git diff --name-only "$BASE_REF"...HEAD -- "$rel" | grep -q .; then
    changed+=("$rel")
  fi
done

if [ "${#changed[@]}" -eq 0 ]; then
  echo "[]"
  exit 0
fi

printf '%s\n' "${changed[@]}" | jq -R . | jq -s .
