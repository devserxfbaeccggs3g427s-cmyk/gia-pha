# Environment ADR — example
#
# This file MUST be accepted (status: Accepted, signed by Platform Lead
# and Security Lead) before any `terraform/envs/example` plan may apply.
# Replace the `selection` placeholders with the chosen managed products.
status: Pending
environment: example
date: 2026-07-27
deciders:
  - Platform Lead
  - Security Lead
  - SRE Lead
selections:
  cloud: TBD
  managedMysql: TBD
  managedKafka: TBD
  managedKms: TBD
  managedObservability: TBD
rationale: |
  TBD — must record availability, security, compliance, cost, HA, DR,
  and operational trade-offs for every selected managed product.
consequences: |
  TBD — must describe HA, DR, secret rotation, schema governance,
  workload identity, and network isolation characteristics.
verification: |
  TBD — must list smoke tests and runbooks required before the first
  production traffic cutover.
