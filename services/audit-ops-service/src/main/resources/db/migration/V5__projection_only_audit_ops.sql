-- V5__projection_only_audit_ops.sql
--
-- Combo A (Tasks 13.1-13.3): Audit Ops is a passive projection. Remove the
-- parallel Saga orchestrator surface (operation_audit registry, saga_state,
-- saga_step) and consolidate around the read-side operation_lifecycle_projection.
-- Add projection_watermark / projection_offset_ledger so the operator UI can
-- expose freshness/lag/gap. Add an immutable audit_evidence table (append-only)
-- to record allowlisted operator intent and lifecycle entries without the
-- legacy operation_audit row.
--
-- Two dedicated dead-letter tables split the two DLQ classes called out by
-- the spec: (a) poison-message DLQ for the lifecycle stream, and (b)
-- poison-message DLQ for the saga-reply stream. Both store topic, partition,
-- offset, payload, error, and timestamp, and link to the originating
-- operation, participant, step, and attempt so operators can replay them.
--
-- Redaction is applied centrally by the application before insert; the
-- redacted_payload column carries the allowlisted JSON only.

DROP TABLE IF EXISTS operation_audit;
DROP TABLE IF EXISTS saga_state;
DROP TABLE IF EXISTS saga_step;
DROP TABLE IF EXISTS saga_dead_letter;

-- Lifecycle (operations.events.v1) poison-message DLQ.
CREATE TABLE lifecycle_dead_letter (
    event_id        CHAR(36)     NOT NULL,
    consumer        VARCHAR(64)  NOT NULL,
    topic           VARCHAR(128) NOT NULL,
    partition_no    INT          NOT NULL,
    offset_no       BIGINT       NOT NULL,
    operation_id    CHAR(36)     NULL,
    error_class     VARCHAR(256) NULL,
    error_message   VARCHAR(2048) NULL,
    redacted_payload_json JSON   NULL,
    quarantined_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (consumer, topic, partition_no, offset_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Saga-reply (saga.replies.v1) poison-message DLQ. Distinct from lifecycle
-- DLQ so operators can replay each stream independently.
CREATE TABLE saga_reply_dead_letter (
    event_id        CHAR(36)     NOT NULL,
    consumer        VARCHAR(64)  NOT NULL,
    topic           VARCHAR(128) NOT NULL,
    partition_no    INT          NOT NULL,
    offset_no       BIGINT       NOT NULL,
    operation_id    CHAR(36)     NULL,
    participant_service VARCHAR(64) NULL,
    step_name       VARCHAR(64)  NULL,
    attempt_count   INT          NULL,
    error_class     VARCHAR(256) NULL,
    error_message   VARCHAR(2048) NULL,
    redacted_payload_json JSON   NULL,
    quarantined_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (consumer, topic, partition_no, offset_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per-topic projection watermark used to expose freshness/lag.
CREATE TABLE projection_watermark (
    topic           VARCHAR(128) NOT NULL,
    consumer_group  VARCHAR(128) NOT NULL,
    last_event_id   CHAR(36)     NULL,
    last_offset     BIGINT       NULL,
    last_partition  INT          NULL,
    last_seen_at    TIMESTAMP(6) NOT NULL,
    record_count    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (topic, consumer_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per-partition gap detection (offsets seen by the consumer). Audit Ops
-- records the last offset and partition observed so the operator UI can
-- surface gaps when the broker drops a record.
CREATE TABLE projection_offset_ledger (
    topic           VARCHAR(128) NOT NULL,
    partition_no    INT          NOT NULL,
    last_offset     BIGINT       NOT NULL,
    last_seen_at    TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (topic, partition_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Allowlisted audit evidence; one row per lifecycle event the consumer
-- accepts and one row per operator intent. The payload column is filtered
-- by the centralized redaction pipeline before insert.
CREATE TABLE audit_evidence (
    event_id        CHAR(36)     NOT NULL,
    operation_id    CHAR(36)     NULL,
    correlation_id  CHAR(36)     NULL,
    causation_id    CHAR(36)     NULL,
    source_service  VARCHAR(64)  NOT NULL,
    actor_user_id   CHAR(36)     NULL,
    actor_kind      VARCHAR(32)  NOT NULL,
    outcome         VARCHAR(32)  NOT NULL,
    reason_code     VARCHAR(64)  NULL,
    payload_json    JSON         NULL,
    occurred_at     TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (event_id),
    KEY ix_audit_evidence_operation (operation_id, occurred_at),
    KEY ix_audit_evidence_source (source_service, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
