-- Saga log for orchestrator services. Per Requirement 10.3.
-- One row per saga; transitions are append-only.

CREATE TABLE saga_log (
    saga_id         CHAR(36) PRIMARY KEY,
    saga_name       VARCHAR(64) NOT NULL,
    actor_user_key  CHAR(36) NULL,
    tree_key        BIGINT NULL,
    correlation_id  VARCHAR(64) NULL,
    state           VARCHAR(32) NOT NULL,
    started_at      TIMESTAMP(6) NOT NULL,
    ended_at        TIMESTAMP(6) NULL,
    idempotency_key CHAR(36) NOT NULL UNIQUE,
    last_error      TEXT NULL,
    INDEX idx_saga_log_state (state, started_at),
    INDEX idx_saga_log_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE saga_step_log (
    saga_id         CHAR(36) NOT NULL,
    step_name       VARCHAR(64) NOT NULL,
    sequence        INT NOT NULL,
    forward_result  JSON NULL,
    compensate_result JSON NULL,
    PRIMARY KEY (saga_id, step_name),
    INDEX idx_step_log_saga (saga_id),
    CONSTRAINT fk_step_log_saga FOREIGN KEY (saga_id) REFERENCES saga_log(saga_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
