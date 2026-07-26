CREATE TABLE upload_intents (
    upload_intent_key       BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    tree_key                BIGINT UNSIGNED     NOT NULL,
    media_key               BIGINT UNSIGNED     NULL,
    requested_by_user_key   BIGINT UNSIGNED     NULL,
    quarantine_object_path  VARCHAR(1024)       NOT NULL,
    final_object_path       VARCHAR(1024)       NOT NULL,
    expected_mime_type      VARCHAR(100)        NOT NULL,
    expected_max_bytes      BIGINT UNSIGNED     NOT NULL,
    expected_sha256         BINARY(32)          NULL,
    status                  VARCHAR(20)         NOT NULL DEFAULT 'PENDING_UPLOAD',
    expires_at              DATETIME(6)         NOT NULL,
    completed_at            DATETIME(6)         NULL,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (upload_intent_key),
    UNIQUE KEY uk_upload_intents_external_id (external_id),
    KEY ix_upload_intents_tree_status (tree_key, status),
    KEY ix_upload_intents_expiry_sweep (status, expires_at),
    CONSTRAINT ck_upload_intents_status
        CHECK (status IN ('PENDING_UPLOAD', 'UPLOADED', 'PROMOTED',
                          'EXPIRED', 'FAILED', 'CANCELLED'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE file_cleanup_jobs (
    file_cleanup_job_key    BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    object_path             VARCHAR(1024)       NOT NULL,
    expected_etag           VARCHAR(255)        NULL,
    reason                  VARCHAR(60)         NOT NULL,
    status                  VARCHAR(15)         NOT NULL DEFAULT 'PENDING',
    attempts                INT UNSIGNED        NOT NULL DEFAULT 0,
    available_at            DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    leased_until            DATETIME(6)         NULL,
    leased_by               VARCHAR(100)        NULL,
    last_error              VARCHAR(2000)       NULL,
    completed_at            DATETIME(6)         NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (file_cleanup_job_key),
    KEY ix_file_cleanup_jobs_claim (status, available_at, file_cleanup_job_key),
    CONSTRAINT ck_file_cleanup_jobs_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'DEAD'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE binary_replicas (
    binary_replica_key      BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    media_key               BIGINT UNSIGNED     NULL,
    primary_object_path     VARCHAR(1024)       NOT NULL,
    primary_sha256          BINARY(32)          NOT NULL,
    archive_object_path     VARCHAR(1024)       NULL,
    archive_sha256          BINARY(32)          NULL,
    status                  VARCHAR(15)         NOT NULL DEFAULT 'PENDING',
    attempts                INT UNSIGNED        NOT NULL DEFAULT 0,
    last_error              VARCHAR(2000)       NULL,
    replicated_at           DATETIME(6)         NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (binary_replica_key),
    UNIQUE KEY uk_binary_replicas_primary_path (primary_object_path(768)),
    KEY ix_binary_replicas_media (media_key),
    KEY ix_binary_replicas_status (status, updated_at),
    CONSTRAINT ck_binary_replicas_status
        CHECK (status IN ('PENDING', 'REPLICATED', 'FAILED', 'ORPHANED'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;