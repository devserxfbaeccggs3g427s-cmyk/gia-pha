-- V3__operation_lifecycle_projection.sql — operator projection of owner-published
-- OperationStarted/OperationStateChanged events. Audit Ops does NOT own the
-- authoritative operation state; this projection is consumed from
-- {@code operations.events.v1} and used for the operator UI and replay only.

CREATE TABLE operation_lifecycle_projection (
    operation_id        CHAR(36)     NOT NULL,
    owner_service       VARCHAR(64)  NOT NULL,
    saga_type           VARCHAR(64)  NOT NULL,
    tree_id             CHAR(36)     NULL,
    initiating_user_id  CHAR(36)     NULL,
    state               VARCHAR(32)  NOT NULL,
    target_version      BIGINT       NULL,
    target_epoch        BIGINT       NULL,
    failure_code        VARCHAR(64)  NULL,
    failure_message     VARCHAR(512) NULL,
    started_at          TIMESTAMP(6) NOT NULL,
    updated_at          TIMESTAMP(6) NOT NULL,
    finalized_at        TIMESTAMP(6) NULL,
    last_event_id       CHAR(36)     NULL,
    PRIMARY KEY (operation_id),
    KEY ix_olp_owner_state (owner_service, state, updated_at),
    KEY ix_olp_tree (tree_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;