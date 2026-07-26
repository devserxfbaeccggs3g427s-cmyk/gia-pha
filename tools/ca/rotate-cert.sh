#!/usr/bin/env bash
# Rotate a single service's leaf cert. Usage: ./tools/ca/rotate-cert.sh <service>
set -euo pipefail
svc="${1:-}"
if [[ -z "${svc}" ]]; then
  echo "usage: $0 <service-name>" >&2
  exit 2
fi

CA_DIR="${CA_DIR:-infra/ca}"
LEAF_DIR="${CA_DIR}/services/${svc}"
if [[ ! -f "${CA_DIR}/ca.crt" ]]; then
  echo "[ca] No CA found. Run 'make ca' first." >&2
  exit 1
fi

mkdir -p "${LEAF_DIR}"
openssl genrsa -out "${LEAF_DIR}/${svc}.key" 2048
cat > "${LEAF_DIR}/${svc}.cnf" <<EOF
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
openssl req -new -key "${LEAF_DIR}/${svc}.key" \
  -out "${LEAF_DIR}/${svc}.csr" \
  -config "${LEAF_DIR}/${svc}.cnf"
openssl x509 -req -in "${LEAF_DIR}/${svc}.csr" \
  -CA "${CA_DIR}/ca.crt" -CAkey "${CA_DIR}/ca.key" \
  -CAcreateserial -out "${LEAF_DIR}/${svc}.crt" \
  -days 1 -sha256 \
  -extfile "${LEAF_DIR}/${svc}.cnf" -extensions v3_req
echo "[ca] Rotated ${svc} certificate."
