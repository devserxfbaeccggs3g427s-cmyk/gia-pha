#!/usr/bin/env bash
# Validate every JSON/YAML schema in infra/schemas/ is parseable.
# Phase 0.1 only requires that the directory exists and is well-formed.
# Phase 0.2 (libs/contract-types) replaces this with a JSON-Schema library
# that fails the build on schema violations.
set -euo pipefail
fail=0
while IFS= read -r -d '' f; do
  case "${f}" in
    *.json) python3 -c "import json,sys; json.load(open(sys.argv[1]))" "${f}" || fail=1 ;;
    *.yaml|*.yml) python3 -c "import sys,yaml; yaml.safe_load(open(sys.argv[1]))" "${f}" || fail=1 ;;
  esac
done < <(find infra/schemas -type f \( -name '*.json' -o -name '*.yaml' -o -name '*.yml' \) -print0 2>/dev/null)
if [[ ${fail} -ne 0 ]]; then
  echo "[schemas] validation failed" >&2
  exit 1
fi
echo "[schemas] ok"
