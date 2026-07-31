-- V1__init.sql — Audit & Operations schema authority (ADR-002, ADR-003, ADR-007).
--
-- Flyway is the schema authority. jOOQ generation reads this schema
-- to produce the typed query DSL. Any change MUST be a new migration.

-- Operation registry. One row per cross-service mutation. The owning
-- service writes this row in the same local transaction that records
-- its domain state and outbox rows. Other services MUST NOT mutate
-- this table; they only project from it via Kafka events.
CREATE TABLE operation_audit (
    id                 CHAR(36)      NOT NULL,
    correlation_id     CHAR(36)      NULL,
    service            VARCHAR(64)   NOT NULL,
    operation_type     VARCHAR(128)  NOT NULL,
    status             VARCHAR(32)   NOT NULL,
    target_revision    BIGINT        NULL,
    target_epoch       BIGINT        NULL,
    aggregate_type     VARCHAR(64)   NULL,
    aggregate_id       VARCHAR(64)   NULL,
    tree_id            CHAR(36)      NULL,
    acting_user        CHAR(36)      NULL,
    detail_json        JSON          NULL,
    error_code         VARCHAR(128)  NULL,
    error_message      VARCHAR(2048) NULL,
    started_at         TIMESTAMP(6)  NOT NULL,
    updated_at         TIMESTAMP(6)  NOT NULL,
    finished_at        TIMESTAMP(6)  NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_operation_status (status, updated_at),
    KEY ix_operation_tree (tree_id, updated_at),
    KEY ix_operation_service (service, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Saga durable state machine. One row per active or terminal Saga
-- instance. Updates are append-only via the version column; the
-- orchestrator reads with FOR UPDATE before each transition.
CREATE TABLE saga_state (
    operation_id       CHAR(36)      NOT NULL,
    saga_type          VARCHAR(64)   NOT NULL,
    current_step       VARCHAR(64)   NOT NULL,
    compensating       BOOLEAN       NOT NULL DEFAULT FALSE,
    step_count         INT           NOT NULL DEFAULT 0,
    last_transition_at TIMESTAMP(6)  NOT NULL,
    payload_json       JSON          NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per-participant step tracking. A Saga has 1..N steps; each step has
-- a required target revision/epoch that the participant MUST ack for
-- the operation to become SUCCEEDED.
CREATE TABLE saga_step (
    operation_id        CHAR(36)      NOT NULL,
    participant_service VARCHAR(64)   NOT NULL,
    step_name           VARCHAR(64)   NOT NULL,
    sequence_no         INT           NOT NULL,
    status              VARCHAR(32)   NOT NULL,
    target_revision     BIGINT        NULL,
    target_epoch        BIGINT        NULL,
    expected_version    BIGINT        NULL,
    acked_at            TIMESTAMP(6)  NULL,
    attempt_count       INT           NOT NULL DEFAULT 0,
    last_error_code     VARCHAR(128)  NULL,
    last_error_message  VARCHAR(2048) NULL,
    last_attempted_at   TIMESTAMP(6)  NULL,
    PRIMARY KEY (operation_id, participant_service, step_name),
    KEY ix_saga_step_status (status, last_attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Append-only audit log. Every cross-service mutation, operator
-- action, and terminal transition appends a row. Rows are NEVER
-- updated or deleted by the service (retention is enforced by an
-- approved scheduled job). Audit & Operations owns this log but
-- MUST NOT use it as business or authorization authority.
CREATE TABLE audit_event (
    audit_id        CHAR(36)      NOT NULL,
    operation_id    CHAR(36)      NULL,
    correlation_id  CHAR(36)      NULL,
    actor_user_id   CHAR(36)      NULL,
    actor_kind      VARCHAR(32)   NOT NULL,
    action          VARCHAR(128)  NOT NULL,
    target_type     VARCHAR(64)   NULL,
    target_id       VARCHAR(64)   NULL,
    detail_json     JSON          NULL,
    occurred_at     TIMESTAMP(6)  NOT NULL,
    trace_id        VARCHAR(64)   NULL,
    PRIMARY KEY (audit_id),
    KEY ix_audit_operation (operation_id, occurred_at),
    KEY ix_audit_actor (actor_user_id, occurred_at),
    KEY ix_audit_action (action, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Outbox, inbox, idempotency are added in V2 to keep this migration
-- purely about the domain tables.