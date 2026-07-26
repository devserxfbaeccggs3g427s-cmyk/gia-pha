# Local development runbook

Single source of truth for: starting the environment, regenerating the
local CA, seeding data, and tearing down.

## 1. Prerequisites

| Tool    | Version (tested) | Notes |
|---------|------------------|-------|
| Docker  | 24+              | Docker Desktop on macOS is fine. |
| Compose | v2 (`docker compose`) | The repo uses Compose v2 syntax. |
| `make`  | 3.81+            | GNU Make; BSD `make` lacks `ifeq`/`findstring`. |
| JDK     | 25 LTS           | Pinned by the monolith spec (ADR-002). |
| Maven   | 3.9+             | `backend/mvnw` is checked in. |
| `openssl` | 3.x            | Local CA generation. |
| `python3` | 3.11+          | Used by `tools/codegen/`. |

## 2. Quick start

```bash
cp .env.example .env
make build-all      # (1) install backend modules + build services
make up             # (2) docker compose up
```

`make build-all` runs `mvn install` against `backend/` (so service POMs
can resolve `vn.giapha:identity-access` etc.) and then `mvn package`
against `services/`.

`make up` brings up MySQL, RabbitMQ, Prometheus, Grafana, and all 12
microservices (11 backend + gateway).

Verify:

```bash
curl -s http://localhost:8080/actuator/health              # gateway
curl -s http://localhost:8081/actuator/health              # identity
curl -s http://localhost:3000/api/health                   # grafana
curl -s http://localhost:9090/-/healthy                    # prometheus
curl -s http://localhost:15672/                            # rabbit
mysql -h 127.0.0.1 -uroot -p$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2) \
  -e "SHOW DATABASES;"
```

Expected databases: `identity`, `tree_content`, `binary_storage`,
`sharing`, `transfer`, `reporting`, `audit`.

## 3. Local CA (mTLS)

```bash
make ca
```

Produces `infra/ca/ca.crt` + per-service leaf certs in
`infra/ca/services/`. Certs are git-ignored.

Rotate a single cert:

```bash
make ca-rotate SERVICE=identity-service
```

## 4. Schemas

Event schemas live in `infra/schemas/`. The schema registry is a
checked-in folder, not a service. Producers and consumers read from
the same folder for the wire-shape contract.

## 5. Seed workflow

```bash
make seed
```

Runs `infra/seed/run.sh`, which applies a snapshot exported from the
monolith. Dependency order:

```
identity -> tree -> members -> relationships -> events -> media-metadata
         -> sharing -> reporting -> audit
```

## 6. Tear-down

```bash
make down         # docker compose down
make down-volumes # also delete MySQL/RabbitMQ data
```

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `vn.giapha:identity-access` not found in service POM | `make build-backend` not run | `make build-backend` then `make build-services` |
| MySQL health check keeps failing | Insufficient Docker memory | Bump to ≥ 4 GB in Docker Desktop. |
| `Access denied for user 'svc_*'` | `.env` out of sync | `make down-volumes && make up`. |
| RabbitMQ port 5672 already in use | Local broker running | Stop the local broker or change the host port. |
| `make ca` fails: `unable to write 'random state'` | Missing `RANDFILE` | `export RANDFILE=$HOME/.rnd && make ca`. |
| Gateway returns 503 to a service | Service not ready yet | Wait 30 s and retry; check `docker compose ps`. |
