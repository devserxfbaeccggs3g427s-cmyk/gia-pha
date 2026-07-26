CREATE TABLE family_trees (
    tree_key                BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (tree_key),
    UNIQUE KEY uk_family_trees_external_id (external_id)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE albums (
    album_key               BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    tree_key                BIGINT UNSIGNED     NOT NULL,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    title                   VARCHAR(200)        NOT NULL,
    description             VARCHAR(2000)       NULL,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (album_key),
    UNIQUE KEY uk_albums_tree_external (tree_key, external_id),
    UNIQUE KEY uk_albums_tree_album (tree_key, album_key),
    CONSTRAINT fk_albums_tree
        FOREIGN KEY (tree_key) REFERENCES family_trees (tree_key)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE media_objects (
    media_key               BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    tree_key                BIGINT UNSIGNED     NOT NULL,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    filename                VARCHAR(255)        NOT NULL,
    original_name           VARCHAR(255)        NOT NULL,
    mime_type               VARCHAR(100)        NOT NULL,
    file_size               BIGINT UNSIGNED     NOT NULL,
    original_object_path    VARCHAR(1024)       NULL,
    original_etag           VARCHAR(255)        NULL,
    original_sha256         BINARY(32)          NULL,
    thumbnail_object_path   VARCHAR(1024)       NULL,
    thumbnail_etag          VARCHAR(255)        NULL,
    thumbnail_sha256        BINARY(32)          NULL,
    caption                 VARCHAR(500)        NULL,
    taken_at                DATETIME(6)         NULL,
    taken_at_precision      VARCHAR(10)         NULL,
    uploaded_by_user_key    BIGINT UNSIGNED     NULL,
    uploaded_at             DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    scan_engine             VARCHAR(100)        NULL,
    scan_result             VARCHAR(20)         NULL,
    scan_signature_version  VARCHAR(100)        NULL,
    scanned_at              DATETIME(6)         NULL,
    replication_status      VARCHAR(20)         NOT NULL DEFAULT 'NONE',
    status                  VARCHAR(20)         NOT NULL,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (media_key),
    UNIQUE KEY uk_media_objects_tree_external (tree_key, external_id),
    UNIQUE KEY uk_media_objects_tree_media (tree_key, media_key),
    KEY ix_media_objects_tree_status (tree_key, status),
    KEY ix_media_objects_status_updated (status, updated_at),
    KEY ix_media_objects_uploader (uploaded_by_user_key),
    CONSTRAINT fk_media_objects_tree
        FOREIGN KEY (tree_key) REFERENCES family_trees (tree_key)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_media_objects_status
        CHECK (status IN ('PENDING_UPLOAD', 'PENDING_SCAN', 'ACTIVE', 'DELETING',
                          'FAILED', 'ORPHANED', 'RETENTION_HELD', 'DELETED')),
    CONSTRAINT ck_media_objects_scan_result
        CHECK (scan_result IS NULL OR scan_result IN ('CLEAN', 'INFECTED', 'ERROR')),
    CONSTRAINT ck_media_objects_replication_status
        CHECK (replication_status IN ('NONE', 'PENDING', 'REPLICATED', 'FAILED')),
    CONSTRAINT ck_media_objects_taken_at_precision
        CHECK (taken_at_precision IS NULL OR taken_at_precision IN ('DAY', 'SECOND')),
    CONSTRAINT ck_media_objects_file_size
        CHECK (file_size > 0)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE media_members (
    tree_key                BIGINT UNSIGNED     NOT NULL,
    media_key               BIGINT UNSIGNED     NOT NULL,
    member_key              BIGINT UNSIGNED     NOT NULL,
    position                INT UNSIGNED        NOT NULL DEFAULT 0,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (tree_key, media_key, member_key),
    KEY ix_media_members_member (tree_key, member_key),
    CONSTRAINT fk_media_members_media_same_tree
        FOREIGN KEY (tree_key, media_key)
        REFERENCES media_objects (tree_key, media_key)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE event_media (
    tree_key                BIGINT UNSIGNED     NOT NULL,
    event_key               BIGINT UNSIGNED     NOT NULL,
    media_key               BIGINT UNSIGNED     NOT NULL,
    position                INT UNSIGNED        NOT NULL DEFAULT 0,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (tree_key, event_key, media_key),
    KEY ix_event_media_media (tree_key, media_key),
    CONSTRAINT fk_event_media_media_same_tree
        FOREIGN KEY (tree_key, media_key)
        REFERENCES media_objects (tree_key, media_key)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE member_avatars (
    tree_key                BIGINT UNSIGNED     NOT NULL,
    member_key              BIGINT UNSIGNED     NOT NULL,
    media_key               BIGINT UNSIGNED     NOT NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (tree_key, member_key),
    KEY ix_member_avatars_media (tree_key, media_key),
    CONSTRAINT fk_member_avatars_media_same_tree
        FOREIGN KEY (tree_key, media_key)
        REFERENCES media_objects (tree_key, media_key)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE album_media (
    tree_key                BIGINT UNSIGNED     NOT NULL,
    media_key               BIGINT UNSIGNED     NOT NULL,
    album_key               BIGINT UNSIGNED     NOT NULL,
    position                INT UNSIGNED        NOT NULL DEFAULT 0,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (tree_key, media_key),
    KEY ix_album_media_album (tree_key, album_key),
    CONSTRAINT fk_album_media_media_same_tree
        FOREIGN KEY (tree_key, media_key)
        REFERENCES media_objects (tree_key, media_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_album_media_album_same_tree
        FOREIGN KEY (tree_key, album_key)
        REFERENCES albums (tree_key, album_key)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;