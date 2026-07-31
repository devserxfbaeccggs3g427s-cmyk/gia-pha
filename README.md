# Family Tree — Backend Monorepo

This repository contains the independently deployable Spring Boot
microservices that replace the legacy Next.js monolith. Architecture,
requirements, and ADRs live in
[`.kiro/specs/spring-boot-backend-migration/`](.kiro/specs/spring-boot-backend-migration/).

## Layout

```
.
├── .kiro/specs/spring-boot-backend-migration/   requirements, design, tasks, ADRs
├── contracts/                                   OpenAPI, event Protobuf, gRPC Protobuf
│   ├── api/v2/openapi.yaml
│   ├── events/                                  Kafka event Protobuf + catalog.yaml
│   ├── grpc/                                    gRPC Protobuf service contracts
│   └── fixtures/                                golden / property / redaction / import / export
├── environments/                                Argo CD GitOps per environment
├── infra/                                       runbooks, schemas
├── platform/
│   ├── starters/                                published platform starters (BOM + 6 starters)
│   ├── helm/                                    reusable Helm chart
│   ├── terraform/                               Terraform modules + per-env compositions
│   └── ci/                                      baseline spike, ci.yml
├── services/                                    one directory per service
│   ├── api-gateway/                             Next.js BFF + edge routing
│   ├── identity-service/                        reference implementation
│   ├── tree-access-service/
│   ├── member-service/
│   ├── relationship-service/
│   ├── event-service/
│   ├── media-service/
│   ├── sharing-service/
│   ├── search-service/
│   ├── transfer-service/
│   ├── audit-ops-service/
│   └── migration-service/
└── tooling/                                     repository-wide scanners and scripts
    ├── affected-services.sh
    ├── architecture-scan/                       architecture/contradiction scanner
    └── service-bootstrap.sh                     generate a new service skeleton
```

## Quick start

```bash
# 1. Run the technology baseline spike
platform/ci/baseline-spike.sh

# 2. Build a single service
mvn -B -ntp -f services/identity-service/pom.xml package

# 3. Run the architecture scanner
javac -d tooling/architecture-scan/build $(find tooling/architecture-scan/src/main/java -name '*.java')
java -cp tooling/architecture-scan/build com.familya.governance.scan.ArchitectureScan
java -cp tooling/architecture-scan/build com.familya.governance.scan.NoRootReactorTest
java -cp tooling/architecture-scan/build com.familya.governance.scan.CrossServiceImportTest

# 4. Bootstrap a new service
tooling/service-bootstrap.sh <service-name> <base-package> <port> <db-name>
```

## Architectural rules

1. Database per service (ADR-002). No cross-service joins.
2. Kafka is the only cross-service event backbone (ADR-004).
3. Cross-service mutations return `202 Accepted` + an `AsyncOperation`
   envelope (ADR-007).
4. No root Maven reactor (ADR-011). Each service builds independently.
5. Domain code has no Spring or JPA dependencies (ArchUnit).
6. No shared business models across services.
