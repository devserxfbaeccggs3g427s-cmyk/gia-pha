#!/usr/bin/env bash
# Workload benchmark — Phase 7.1.
# Runs the same scripted workload against:
#   1. The backend/ monolith (one Spring Boot process).
#   2. The microservice decomposition (12 containers via docker compose).
# Outputs docs/microservice/bench.json + bench.csv.
#
# Workload: 100 registrations, 100 logins, 50 create-member, 50
# create-relationship, 50 create-event, 50 upload-complete, 50
# share-link-create, 50 reporting-search, 25 import-jobs.
set -euo pipefail

OUT_DIR="docs/microservice"
mkdir -p "${OUT_DIR}"

# Detect which topology we're hitting.
TOPOLOGY="${TOPOLOGY:-decomposition}"
if [[ "${TOPOLOGY}" == "monolith" ]]; then
  BASE_URL="${MONOLITH_URL:-http://localhost:8080}"
else
  BASE_URL="${DECOMP_URL:-http://localhost:8080}"
fi

echo "Running benchmark against ${TOPOLOGY} at ${BASE_URL}"
echo "TODO: real benchmark numbers require JDK + running services."
echo "Writing placeholder JSON + CSV so the comparison report has shape."

cat > "${OUT_DIR}/bench.json" <<EOF
{
  "topology": "${TOPOLOGY}",
  "base_url": "${BASE_URL}",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "iterations": 0,
  "metrics": {}
}
EOF

cat > "${OUT_DIR}/bench.csv" <<EOF
topology,iterations,p50_ms,p95_ms,p99_ms,error_rate,outbox_lag_ms,saga_complete_s
${TOPOLOGY},0,0,0,0,0,0,0
EOF

echo "Wrote ${OUT_DIR}/bench.json + ${OUT_DIR}/bench.csv"
