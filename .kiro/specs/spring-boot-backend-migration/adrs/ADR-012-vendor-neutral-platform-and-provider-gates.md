# ADR-012: Vendor-Neutral Platform and Provider Gates

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

The target requires repeatable infrastructure, packaging, and GitOps promotion, while the cloud and managed product providers remain environment-specific and have not been selected.

## Decision

Standardize Kubernetes, Helm, Terraform, and Argo CD as the vendor-neutral platform toolchain. Helm packages each service, Terraform provisions infrastructure, and Argo CD reconciles reviewed environment state and promotes immutable image digests. Each environment must accept an ADR selecting its cloud and managed MySQL, Kafka, KMS, and observability products before those products are provisioned.

## Consequences

Portable tooling and provider-specific modules coexist. Provider selection cannot occur implicitly in implementation, and environment ADRs must record availability, security, compliance, cost, HA, DR, and operational trade-offs.

## Verification

CI validates Terraform and Helm, GitOps promotion deploys the same signed image digest, policy tests enforce isolation and supply-chain controls, and provisioning fails when the required environment ADR is absent.
