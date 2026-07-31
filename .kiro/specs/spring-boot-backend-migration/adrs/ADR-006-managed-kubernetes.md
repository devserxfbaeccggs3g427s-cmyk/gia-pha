# ADR-006: Managed Kubernetes Platform

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

The target includes independently deployed HTTP services, Kafka consumers, isolated workers, autoscaling, workload identities, network isolation, and coordinated observability. These are core operational requirements rather than optional future optimization.

## Decision

Run production services on managed multi-availability-zone Kubernetes. Isolate environments and domains with namespaces, service accounts, workload identity/mTLS, NetworkPolicies, quotas, PodDisruptionBudgets, topology spread, and private service discovery. Use HPA/KEDA for HTTP and Kafka lag. Apply the Helm, Terraform, Argo CD, and provider-selection rules in ADR-012.

Use managed MySQL 8.4 and managed Kafka-compatible infrastructure rather than in-cluster stateful production databases/brokers. Secrets reside in managed secret storage/KMS. Signed OCI images run non-root with read-only filesystems and are promoted by immutable digest with SBOM/provenance.

## Consequences

Kubernetes and cloud platform skills, cost controls, runbooks, and policy automation are required before application cutover. Managed services reduce but do not remove HA/DR responsibility.

## Verification

Multi-AZ failover, network/identity isolation, secret rotation, scaling, disruption, restore, signed-image admission, observability, and dark-deployment tests must pass before production traffic.
