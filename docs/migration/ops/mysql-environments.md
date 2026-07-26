# MySQL 8.4 Environments

Spec: spring-boot-backend-migration — Task 8 (Req 12.5-12.6, 14.1, 14.9-14.10).

## Version pinning (8.1)

| Environment | Engine | Source of truth |
| --- | --- | --- |
| Local | `mysql:8.4` (docker-compose) | `backend/docker-compose.yml` |
| Integration tests | `mysql:8.4` (Testcontainers) | `MySqlTestSupport.MYSQL_IMAGE` |
| Staging / Production | Managed MySQL pinned to the 8.4 LTS series | provisioning IaC |

The three sources must always name the same major/minor; bumping requires an ADR-002 revision.

## Server parameters (8.2)

Identical across environments (local file: `backend/infra/mysql/conf/giapha.cnf`):

- `default-time-zone='+00:00'`; JDBC adds `connectionTimeZone=UTC`.
- `sql_mode` strict set: `STRICT_TRANS_TABLES,STRICT_ALL_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION,ONLY_FULL_GROUP_BY`.
- `character-set-server=utf8mb4`, `collation-server=utf8mb4_0900_ai_ci`.
- TLS: managed environments set `require_secure_transport=ON` and the JDBC URL uses
  `sslMode=VERIFY_CA` with the provider CA bundle; local uses `sslMode=PREFERRED`.
- Private networking: staging/production instances have **no public endpoint**; the
  application reaches them over the VPC/private link only. Security groups allow port 3306
  solely from the application subnets and the bastion used for break-glass access.
- HikariCP (application side): `maximum-pool-size` 10 per instance (default), sized so
  `instances × pool-max < max_connections × 0.8`; `connection-timeout` 5 s,
  `max-lifetime` 30 min (below the provider's idle cutoff).

## Identities (8.3)

Same four identities everywhere; local passwords are dev-only, managed passwords live in the
secret store and rotate via MySQL dual-password (see `secret-rotation.md`).

| Identity | Grants | Used by |
| --- | --- | --- |
| `giapha_migrate` | DML + `CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES` on `giapha.*` | Flyway only (`spring.flyway.user`) |
| `giapha_app` | `SELECT, INSERT, UPDATE, DELETE` on `giapha.*` — **no DDL** | Spring runtime (`spring.datasource`) |
| `giapha_backup` | `SELECT, LOCK TABLES, SHOW VIEW, TRIGGER` on `giapha.*`; global `RELOAD, PROCESS, REPLICATION CLIENT` | Backup tooling / dump verification |
| `giapha_reporting` | `SELECT` on `giapha.*` | Reconciliation + BI tooling |

DoD check: connecting as `giapha_app` and issuing `ALTER TABLE`/`CREATE TABLE` must fail with
`ERROR 1142`; access to any schema other than `giapha` must fail with `ERROR 1044`.

## HA, backups and PITR (8.4)

Managed staging/production requirements (enforced via provisioning IaC):

- **HA:** multi-AZ instance with automatic failover; the application reconnects through the
  stable endpoint (HikariCP `max-lifetime` bounds stale connections after failover).
- **Backups:** automated daily snapshots, encrypted at rest (KMS-managed key), retained 35
  days — covering the RPO ≤ 5 min / RTO ≤ 4 h targets in `nfr-threat-model.md`.
- **Binlogs:** `ROW` format, retained ≥ 7 days, shipped continuously by the provider for
  point-in-time recovery to any second within the retention window.
- **PITR drill:** quarterly restore of production into an isolated instance, followed by the
  Task 47 reconciliation counts against the live database; drill results recorded in the ops
  log.
- Local approximation: binlogs enabled in `giapha.cnf` so recovery procedures can be
  rehearsed with `mysqlbinlog` against the compose volume.
