CREATE TABLE audit_logs (
    audit_log_key           BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    tree_key                BIGINT UNSIGNED     NOT NULL,
    actor_user_external_id  VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    entity_type             VARCHAR(20)         NOT NULL,
    entity_external_id      VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    member_external_id      VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    action                  VARCHAR(10)         NOT NULL,
    field_changed           VARCHAR(200)        NULL,
    previous_data           JSON                NULL,
    new_data                JSON                NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (audit_log_key),
    UNIQUE KEY uk_audit_logs_external_id (external_id),
    KEY ix_audit_logs_tree_created (tree_key, created_at),
    KEY ix_audit_logs_tree_entity (tree_key, entity_type, entity_external_id),
    KEY ix_audit_logs_tree_member (tree_key, member_external_id),
    CONSTRAINT fk_audit_logs_tree
        FOREIGN KEY (tree_key) REFERENCES family_trees (tree_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_audit_logs_action
        CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_audit_logs_entity_type
        CHECK (entity_type IN ('MEMBER', 'RELATIONSHIP', 'EVENT', 'MEDIA'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE security_audit_logs (
    security_audit_log_key  BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    event_type              VARCHAR(60)         NOT NULL,
    outcome                 VARCHAR(10)         NOT NULL,
    user_external_id        VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    email_hash              BINARY(32)          NULL,
    ip_hash                 BINARY(32)          NULL,
    user_agent              VARCHAR(400)        NULL,
    detail                  JSON                NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (security_audit_log_key),
    KEY ix_security_audit_logs_type_created (event_type, created_at),
    KEY ix_security_audit_logs_user (user_external_id, created_at),
    CONSTRAINT ck_security_audit_logs_outcome
        CHECK (outcome IN ('SUCCESS', 'FAILURE', 'BLOCKED'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE processed_commands (
    processed_command_key   BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    idempotency_scope       VARCHAR(200)        NOT NULL,
    idempotency_key         VARCHAR(200)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    request_hash            BINARY(32)          NOT NULL,
    response_status         SMALLINT UNSIGNED   NULL,
    response_body           JSON                NULL,
    completed_at            DATETIME(6)         NULL,
    expires_at              DATETIME(6)         NOT NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (processed_command_key),
    UNIQUE KEY uk_processed_commands_scope_key (idempotency_scope, idempotency_key),
    KEY ix_processed_commands_expiry (expires_at)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE outbox_events (
    outbox_event_key        BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    aggregate_type          VARCHAR(60)         NOT NULL,
    aggregate_external_id   VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    tree_key                BIGINT UNSIGNED     NULL,
    event_type              VARCHAR(100)        NOT NULL,
    payload                 JSON                NOT NULL,
    status                  VARCHAR(15)         NOT NULL DEFAULT 'PENDING',
    attempts                INT UNSIGNED        NOT NULL DEFAULT 0,
    available_at            DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    leased_until            DATETIME(6)         NULL,
    leased_by               VARCHAR(100)        NULL,
    last_error              VARCHAR(2000)       NULL,
    completed_at            DATETIME(6)         NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (outbox_event_key),
    KEY ix_outbox_events_claim (status, available_at, outbox_event_key),
    KEY ix_outbox_events_aggregate (aggregate_type, aggregate_external_id),
    CONSTRAINT ck_outbox_events_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'DEAD'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;