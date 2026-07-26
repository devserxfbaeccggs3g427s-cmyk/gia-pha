CREATE TABLE import_jobs (
    import_job_key          BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    tree_key                BIGINT UNSIGNED     NOT NULL,
    requested_by_user_key   BIGINT UNSIGNED     NULL,
    input_format            VARCHAR(10)         NOT NULL,
    input_checksum          BINARY(32)          NOT NULL,
    input_bytes             BIGINT UNSIGNED     NOT NULL,
    mode                    VARCHAR(10)         NOT NULL,
    duplicate_strategy      VARCHAR(10)         NOT NULL DEFAULT 'SKIP',
    status                  VARCHAR(15)         NOT NULL DEFAULT 'PENDING',
    total_count             INT UNSIGNED        NOT NULL DEFAULT 0,
    accepted_count          INT UNSIGNED        NOT NULL DEFAULT 0,
    skipped_count           INT UNSIGNED        NOT NULL DEFAULT 0,
    error_count             INT UNSIGNED        NOT NULL DEFAULT 0,
    errors                  JSON                NULL,
    completed_at            DATETIME(6)         NULL,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (import_job_key),
    UNIQUE KEY uk_import_jobs_external_id (external_id),
    KEY ix_import_jobs_tree_created (tree_key, created_at),
    CONSTRAINT fk_import_jobs_tree
        FOREIGN KEY (tree_key) REFERENCES family_trees (tree_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_import_jobs_requester
        FOREIGN KEY (requested_by_user_key) REFERENCES users (user_key)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_import_jobs_input_format
        CHECK (input_format IN ('JSON', 'CSV', 'GEDCOM')),
    CONSTRAINT ck_import_jobs_mode
        CHECK (mode IN ('PREVIEW', 'EXECUTE')),
    CONSTRAINT ck_import_jobs_duplicate_strategy
        CHECK (duplicate_strategy IN ('SKIP', 'REPLACE', 'MERGE')),
    CONSTRAINT ck_import_jobs_status
        CHECK (status IN ('PENDING', 'VALIDATING', 'PREVIEWED', 'APPLYING',
                          'COMPLETED', 'FAILED', 'CANCELLED'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE generated_artifact_jobs (
    artifact_job_key        BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    tree_key                BIGINT UNSIGNED     NOT NULL,
    owner_user_key          BIGINT UNSIGNED     NOT NULL,
    operation               VARCHAR(30)         NOT NULL,
    option_hash             BINARY(32)          NOT NULL,
    options                 JSON                NULL,
    status                  VARCHAR(15)         NOT NULL DEFAULT 'PENDING',
    progress_percent        TINYINT UNSIGNED    NOT NULL DEFAULT 0,
    cancel_requested        TINYINT(1)          NOT NULL DEFAULT 0,
    result_object_path      VARCHAR(1024)       NULL,
    result_checksum         BINARY(32)          NULL,
    result_bytes            BIGINT UNSIGNED     NULL,
    expires_at              DATETIME(6)         NULL,
    last_error              VARCHAR(2000)       NULL,
    completed_at            DATETIME(6)         NULL,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (artifact_job_key),
    UNIQUE KEY uk_generated_artifact_jobs_external_id (external_id),
    KEY ix_generated_artifact_jobs_tree_op (tree_key, operation, option_hash, status),
    KEY ix_generated_artifact_jobs_owner (owner_user_key, created_at),
    KEY ix_generated_artifact_jobs_expiry (status, expires_at),
    CONSTRAINT fk_generated_artifact_jobs_tree
        FOREIGN KEY (tree_key) REFERENCES family_trees (tree_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_generated_artifact_jobs_owner
        FOREIGN KEY (owner_user_key) REFERENCES users (user_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_generated_artifact_jobs_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED',
                          'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_generated_artifact_jobs_progress
        CHECK (progress_percent <= 100)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE tree_snapshots (
    snapshot_key            BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    tree_key                BIGINT UNSIGNED     NOT NULL,
    schema_version          VARCHAR(20)         NOT NULL,
    object_path             VARCHAR(1024)       NOT NULL,
    checksum                BINARY(32)          NOT NULL,
    payload_bytes           BIGINT UNSIGNED     NOT NULL,
    member_count            INT UNSIGNED        NOT NULL DEFAULT 0,
    relationship_count      INT UNSIGNED        NOT NULL DEFAULT 0,
    event_count             INT UNSIGNED        NOT NULL DEFAULT 0,
    media_count             INT UNSIGNED        NOT NULL DEFAULT 0,
    trigger_source          VARCHAR(15)         NOT NULL DEFAULT 'MANUAL',
    retain_until            DATETIME(6)         NULL,
    created_by_user_key     BIGINT UNSIGNED     NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (snapshot_key),
    UNIQUE KEY uk_tree_snapshots_external_id (external_id),
    KEY ix_tree_snapshots_tree_created (tree_key, created_at),
    KEY ix_tree_snapshots_retention (retain_until),
    CONSTRAINT fk_tree_snapshots_tree
        FOREIGN KEY (tree_key) REFERENCES family_trees (tree_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_tree_snapshots_creator
        FOREIGN KEY (created_by_user_key) REFERENCES users (user_key)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_tree_snapshots_trigger
        CHECK (trigger_source IN ('MANUAL', 'SCHEDULED', 'PRE_IMPORT', 'PRE_RESTORE'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE migration_ledger (
    migration_ledger_key    BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    manifest_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source_pathname         VARCHAR(1024)       NOT NULL,
    source_etag             VARCHAR(255)        NOT NULL,
    canonical_hash          BINARY(32)          NOT NULL,
    entity_type             VARCHAR(30)         NOT NULL,
    entity_external_id      VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    status                  VARCHAR(15)         NOT NULL DEFAULT 'STAGED',
    detail                  JSON                NULL,
    migrated_at             DATETIME(6)         NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (migration_ledger_key),
    UNIQUE KEY uk_migration_ledger_source (manifest_id, source_pathname(255), source_etag),
    KEY ix_migration_ledger_status (status),
    KEY ix_migration_ledger_entity (entity_type, entity_external_id),
    CONSTRAINT ck_migration_ledger_status
        CHECK (status IN ('STAGED', 'TRANSFORMED', 'LOADED', 'SKIPPED', 'FAILED'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;