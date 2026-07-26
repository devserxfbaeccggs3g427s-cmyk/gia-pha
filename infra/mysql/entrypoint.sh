#!/bin/bash
# Substitute MYSQL_SVC_* placeholders in init scripts with the values from
# the environment (or the documented defaults) before delegating to the
# stock MySQL entrypoint. Operates only on /docker-entrypoint-initdb.d.
set -euo pipefail

INIT_DIR="/docker-entrypoint-initdb.d"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# Defaults mirror src/main/resources/application.yml per-service fallbacks
# so a fresh `docker compose up` produces a usable database without an .env.
MYSQL_SVC_IDENTITY="${MYSQL_SVC_IDENTITY:-identitypw}"
MYSQL_SVC_TREE="${MYSQL_SVC_TREE:-trepw}"
MYSQL_SVC_MEMBERS="${MYSQL_SVC_MEMBERS:-mempw}"
MYSQL_SVC_RELATIONS="${MYSQL_SVC_RELATIONS:-relpw}"
MYSQL_SVC_EVENTS="${MYSQL_SVC_EVENTS:-evpw}"
MYSQL_SVC_MEDIA="${MYSQL_SVC_MEDIA:-medpw}"
MYSQL_SVC_BINARY="${MYSQL_SVC_BINARY:-binpw}"
MYSQL_SVC_SHARING="${MYSQL_SVC_SHARING:-shapw}"
MYSQL_SVC_TRANSFER="${MYSQL_SVC_TRANSFER:-trapw}"
MYSQL_SVC_REPORTING="${MYSQL_SVC_REPORTING:-reppw}"
MYSQL_SVC_AUDIT="${MYSQL_SVC_AUDIT:-audpw}"
export MYSQL_SVC_IDENTITY MYSQL_SVC_TREE MYSQL_SVC_MEMBERS MYSQL_SVC_RELATIONS \
       MYSQL_SVC_EVENTS MYSQL_SVC_MEDIA MYSQL_SVC_BINARY MYSQL_SVC_SHARING \
       MYSQL_SVC_TRANSFER MYSQL_SVC_REPORTING MYSQL_SVC_AUDIT

for f in "$INIT_DIR"/*.sql; do
  [ -e "$f" ] || continue
  out="$TMP_DIR/$(basename "$f")"
  # Protect against literal $ characters in SQL (none today, but cheap).
  sed 's/\$$/\$\$/g' "$f" \
    | envsubst '__MYSQL_SVC_.*__' > "$out"
  cp "$out" "$f"
done

exec docker-entrypoint.sh "$@"
