-- V2__saga_participant.sql — local Saga participant support tables.

CREATE TABLE saga_compensation_snapshot (
    operation_id   CHAR(36)     NOT NULL,
    snapshot_json  JSON         NOT NULL,
    recorded_at    TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE saga_reply_outbox (
    id                 CHAR(36)     NOT NULL,
    operation_id       CHAR(36)     NOT NULL,
    participant        VARCHAR(64)  NOT NULL,
    step_code          VARCHAR(64)  NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    applied_version    BIGINT       NULL,
    applied_epoch      BIGINT       NULL,
    failure_code       VARCHAR(64)  NULL,
    failure_message    VARCHAR(512) NULL,
    occurred_at        TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_reply_op (operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;