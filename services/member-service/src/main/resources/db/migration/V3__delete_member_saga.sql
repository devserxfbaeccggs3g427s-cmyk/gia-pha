-- V3__delete_member_saga.sql — owner-side Saga persistence for delete-member.
--
-- The Member service owns the delete-member Saga (ADR-003). These tables hold
-- the authoritative Saga state, the planned participant steps, the per-step
-- attempt/dispatch metadata, and the compensation snapshot required to undo
-- a step before the irreversible boundary (final tree revision advance).

CREATE TABLE delete_member_saga_state (
    operation_id              CHAR(36)     NOT NULL,
    tree_id                   CHAR(36)     NOT NULL,
    member_id                 CHAR(36)     NOT NULL,
    initiating_user_id        CHAR(36)     NOT NULL,
    correlation_id            CHAR(36)     NOT NULL,
    state                     VARCHAR(32)  NOT NULL,
    target_aggregate_version  BIGINT       NOT NULL,
    target_epoch              BIGINT       NOT NULL,
    deadline_at               TIMESTAMP(6) NOT NULL,
    started_at                TIMESTAMP(6) NOT NULL,
    finalized_at              TIMESTAMP(6) NULL,
    last_updated_at           TIMESTAMP(6) NOT NULL,
    irreversible_at           TIMESTAMP(6) NULL,
    failure_code              VARCHAR(64)  NULL,
    failure_message           VARCHAR(512) NULL,
    PRIMARY KEY (operation_id),
    KEY ix_dm_state_tree (tree_id, state, last_updated_at),
    KEY ix_dm_state_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE delete_member_saga_step (
    operation_id              CHAR(36)     NOT NULL,
    sequence_no               INT          NOT NULL,
    step_code                 VARCHAR(64)  NOT NULL,
    participant_service       VARCHAR(64)  NOT NULL,
    required                  BOOLEAN      NOT NULL DEFAULT TRUE,
    compensatable             BOOLEAN      NOT NULL DEFAULT TRUE,
    state                     VARCHAR(32)  NOT NULL,
    attempt_count             INT          NOT NULL DEFAULT 0,
    max_attempts              INT          NOT NULL DEFAULT 5,
    last_dispatched_at        TIMESTAMP(6) NULL,
    last_reply_at             TIMESTAMP(6) NULL,
    applied_aggregate_version BIGINT       NULL,
    applied_epoch             BIGINT       NULL,
    failure_code              VARCHAR(64)  NULL,
    failure_message           VARCHAR(512) NULL,
    PRIMARY KEY (operation_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE delete_member_compensation_snapshot (
    operation_id              CHAR(36)     NOT NULL,
    participant_service       VARCHAR(64)  NOT NULL,
    snapshot_json             JSON         NOT NULL,
    recorded_at               TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (operation_id, participant_service)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;