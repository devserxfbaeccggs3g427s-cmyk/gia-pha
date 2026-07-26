# CI/CD & Supply-Chain Controls

Spec: spring-boot-backend-migration — Task 42 (Req 15.10-15.11, 18.7-18.8).

## Pipeline gates

| Stage | Tool | Failure mode |
| --- | --- | --- |
| Format | `spotless apply` / `mvn -q fmt:check` | CI fails on whitespace/diff |
| Static analysis | `mvn -q spotbugs:check pmd:check` | CI fails on any warning |
| Architecture | `ModularArchitectureTest` (ArchUnit) | CI fails on boundary leak |
| Tests | `mvn -q verify` against MySQL 8.4 Testcontainer | CI fails on regression |
| SBOM | `cyclonedx-maven-plugin` (CycloneDX 1.5) | Build fails on missing SBOM |
| SCA | `dependency-check-maven` OWASP NVD feed | Build fails on critical/high CVE |
| Secret scan | `gitleaks` against every PR | CI fails on any commit |
| Container scan | `trivy fs` and `trivy image` against the built image | Promotion fails on critical/high |
| IaC scan | `conftest` against `tfsec`/`kics` output | Promotion fails on critical/high |
| License scan | `mvn license:check` (allowlist: Apache-2.0, MIT, BSD-2/3, EPL-2.0) | Build fails on unknown license |

## Supply-chain provenance

- **Image digest pinning:** every Dockerfile uses `FROM --platform=linux/amd64
  <digest>@<image>` — never a floating tag.
- **Signing:** `cosign sign --key <kms>` on every image. The public key is
  pinned in the cluster admission policy (`Kyverno verifyImages`).
- **Provenance:** SLSA Level 3 attestation built by `slsa-github-generator`,
  committed to the OCI registry as a referrer artifact.
- **Promote:** one immutable artifact is promoted across `dev → staging →
  prod`; production runs the exact digest that staging passed.

## Rollback artifact retention

Each release keeps the previous `n=3` immutable artifacts under the same
digest in the registry so a verified rollback is always possible without
rebuilding. The `releases.json` index records digest → commit → operator.

## Local dry-run

```bash
make ci-gates   # spotless + spotbugs + archunit + tests + sbom + sca
make image      # builds + scans + signs + attaches provenance
```
