# Task 3 — Provision the managed platform foundation

This task delivers the vendor-neutral platform required by ADR-006 and
ADR-012. The cloud and managed MySQL/Kafka/KMS/observability products
are environment-specific; they are gated on an accepted environment ADR
before provisioning.

## 3.1 Environment ADRs

`environments/<env>/ADR.md` is the gate. Until an environment ADR is
accepted, the corresponding `terraform/envs/<env>/` directory is
ignored by the GitOps reconciler.

## 3.2 Managed multi-AZ Kubernetes

`platform/terraform/modules/managed-kubernetes` provisions:

- Multi-AZ control plane and node pools
- Gateway/Ingress (NGINX or vendor equivalent) with mTLS
- Namespaces per service, service accounts, workload identity
- NetworkPolicies (deny-by-default, allow-list per service)
- HPA, KEDA, PodDisruptionBudgets, topology spread, resource quotas

## 3.3 Managed MySQL 8.4 + Kafka

`platform/terraform/modules/managed-mysql` provisions an isolated
MySQL 8.4 instance per service with private endpoints, HA, encrypted
backups, binlogs, PITR, and per-service credentials.

`platform/terraform/modules/managed-kafka` provisions a managed
Kafka-compatible cluster with Schema Registry, multi-AZ brokers,
per-topic ACLs, encryption, and DR configuration.

## 3.4 Secrets, GitOps, Helm, Argo CD

`platform/terraform/modules/managed-kms` integrates the cloud KMS for
secret encryption. `environments/<env>/gitops/` contains the
Argo CD `Application` and `AppProject` for every service, each pinned
to a signed image digest in the GitOps repo. Helm charts live in
`platform/helm/templates/` and are reused per service.

## 3.5 Observability

`platform/terraform/modules/managed-observability` provisions the
OpenTelemetry collector, the metrics backend, the log backend, and
the dashboards. The starter at
`platform/starters/platform-observability-starter` wires every
service to the collector.

## 3.6 Supply chain

- OCI images are signed with `cosign`, include SBOM and SLSA
  provenance, run non-root with read-only filesystems.
- Argo CD promotes the same immutable digest between environments
  through reviewed Git state.
- HPA/KEDA, PodDisruptionBudgets, topology spread, and resource
  quotas are mandatory in every chart.

## Acceptance

- Required environment ADRs exist.
- Terraform plan + Helm template validation pass in CI.
- Isolation and Kafka schema/ACL tests pass.
- Argo CD promotes the same signed image digest between environments.
- Platform failover, secret rotation, and telemetry smoke tests pass.
