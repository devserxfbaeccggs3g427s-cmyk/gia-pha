-- V4__saga_dead_letter.sql — durable poison-message store for owner-driven Sagas.
-- A row is written when a participant reply cannot be parsed (POISON), when the
-- participant retry budget is exhausted (RETRY_EXHAUSTED), or when in-listener
-- processing throws a non-retryable error. The unique key on
-- (consumer, source_kind, source_key) guarantees idempotency: the same poison
-- Kafka offset cannot be recorded twice and the same exhausted-step attempt
-- cannot produce two DLQ rows.

CREATE TABLE member_saga_dead_letter (
    id                   CHAR(36)     NOT NULL,
    consumer             VARCHAR(128) NOT NULL,
    source_kind          VARCHAR(32)  NOT NULL,
    source_key           VARCHAR(255) NOT NULL,
    operation_id         CHAR(36)     NULL,
    saga_type            VARCHAR(64)  NULL,
    participant_service  VARCHAR(64)  NULL,
    step_code            VARCHAR(64)  NULL,
    attempt_count        INT          NULL,
    topic                VARCHAR(255) NOT NULL,
    partition_id         INT          NOT NULL,
    offset_value         BIGINT       NOT NULL,
    payload_text         MEDIUMTEXT   NULL,
    error_class          VARCHAR(255) NULL,
    error_message        VARCHAR(2048) NULL,
    occurred_at          TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_saga_dl_source (consumer, source_kind, source_key),
    KEY ix_member_saga_dl_operation (operation_id, occurred_at),
    KEY ix_member_saga_dl_occurred_at (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;