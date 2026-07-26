#!/usr/bin/env bash
# Generate a local CA and per-service leaf certs for mTLS.
# Per Requirements 13.1 (mTLS) and the design's Security Model section.
# Idempotent: refuses to overwrite an existing CA without --force.
set -euo pipefail

CA_DIR="${CA_DIR:-infra/ca}"
SERVICE_DIR="${CA_DIR}/services"
DAYS_CA="${DAYS_CA:-3650}"
DAYS_LEAF="${DAYS_LEAF:-1}"   # 24h rotation per design.md
CN_CA="giapha-research-ca"

force=0
if [[ "${1:-}" == "--force" ]]; then force=1; fi

mkdir -p "${CA_DIR}" "${SERVICE_DIR}"

if [[ -f "${CA_DIR}/ca.crt" && ${force} -eq 0 ]]; then
  echo "[ca] CA already exists at ${CA_DIR}/ca.crt (use --force to regenerate)."
  exit 0
fi

echo "[ca] Generating root CA..."
openssl genrsa -out "${CA_DIR}/ca.key" 4096
openssl req -x509 -new -nodes \
  -key "${CA_DIR}/ca.key" \
  -sha256 -days "${DAYS_CA}" \
  -subj "/CN=${CN_CA}/O=GiaPha Research" \
  -out "${CA_DIR}/ca.crt"

# Required for newer openssl versions.
touch "${CA_DIR}/index.txt"
echo "01" > "${CA_DIR}/serial"

# Discover services from the services/ directory if it exists; otherwise
# generate a placeholder so the CA is usable as phases add services.
if compgen -G "services/*" >/dev/null; then
  services=$(ls -d services/*/ 2>/dev/null | xargs -n1 basename)
else
  services=(
    identity-service tree-service members-service relationships-service
    events-service media-metadata-service binary-storage-service
    sharing-service transfer-service reporting-service audit-service
  )
fi

for svc in "${services[@]}"; do
  leaf_dir="${SERVICE_DIR}/${svc}"
  mkdir -p "${leaf_dir}"
  if [[ -f "${leaf_dir}/${svc}.crt" && ${force} -eq 0 ]]; then
    continue
  fi
  echo "[ca] Issuing leaf cert for ${svc}..."
  openssl genrsa -out "${leaf_dir}/${svc}.key" 2048
  cat > "${leaf_dir}/${svc}.cnf" <<EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no
[req_distinguished_name]
CN = ${svc}
O = GiaPha Research
[v3_req]
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = @alt_names
[alt_names]
DNS.1 = ${svc}
DNS.2 = ${svc}.giapha-net
DNS.3 = localhost
EOF
  openssl req -new -key "${leaf_dir}/${svc}.key" \
    -out "${leaf_dir}/${svc}.csr" \
    -config "${leaf_dir}/${svc}.cnf"
  openssl x509 -req -in "${leaf_dir}/${svc}.csr" \
    -CA "${CA_DIR}/ca.crt" -CAkey "${CA_DIR}/ca.key" \
    -CAcreateserial -out "${leaf_dir}/${svc}.crt" \
    -days "${DAYS_LEAF}" -sha256 \
    -extfile "${leaf_dir}/${svc}.cnf" -extensions v3_req
done

echo "[ca] Done. Mount ${CA_DIR} as a volume into each service container."
