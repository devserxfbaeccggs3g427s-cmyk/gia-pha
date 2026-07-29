# ADR-004: Mandatory Kafka Governance

- **Status:** Accepted
- **Date:** 2026-07-27

## Context

The target depends on domain events, Saga commands/replies, authorization and domain projections, cleanup, replication, and migration progress. An optional broker would leave core correctness without a defined backbone.

## Decision

A managed Kafka-compatible platform and Schema Registry are mandatory. Topics are versioned by bounded context and partitioned by `treeId`, except identity topics keyed by `userId`. Protobuf event schemas use backward compatibility according to ADR-010. Producers use a transactional outbox; consumers use inbox deduplication, aggregate versions, bounded retry with jitter, DLQ, replay, and gap reconciliation.

Messages carry event, correlation, causation, operation, aggregate version, occurrence time, schema version, and trace context. Topic ACLs enforce least privilege. Secrets, raw Blob URLs, and non-allowlisted PII are prohibited. End-to-end exactly-once delivery is not claimed.

## Consequences

Kafka availability, lag, schema evolution, partition skew, replay, and DR become operational responsibilities. Writes may commit to outbox during a bounded outage, but mutation admission is blocked when outbox age exceeds policy.

## Verification

CI schema compatibility/privacy gates, ACL tests, duplicate/reorder/replay/broker-outage integration tests, consumer-lag alerts, DLQ tooling, and Kafka recovery drills are required.
