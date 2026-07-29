-- V1__init.sql — media-service schema authority (ADR-002, ADR-009).
--
-- Media owns media/album metadata, associations, upload intents,
-- quarantine, mandatory scanning, thumbnails, tombstones, retention,
-- cleanup, and binary replication. References to members/events/
-- albums are opaque external IDs; the local projection table tracks
-- availability for fail-closed association validation.

CREATE TABLE media_asset (
    id                  CHAR(36)      NOT NULL,
    tree_id             CHAR(36)      NOT NULL,
    album_id            CHAR(36)      NULL,
    owner_user_id       CHAR(36)      NOT NULL,
    kind                VARCHAR(16)   NOT NULL,
    mime_type           VARCHAR(128)  NOT NULL,
    byte_size           BIGINT        NOT NULL,
    sha256              CHAR(64)      NOT NULL,
    original_filename   VARCHAR(512)  NULL,
    status              VARCHAR(16)   NOT NULL,
    quarantine_path     VARCHAR(1024) NULL,
    promoted            BOOLEAN       NOT NULL DEFAULT FALSE,
    retention_hold_until TIMESTAMP(6) NULL,
    tombstoned_at       TIMESTAMP(6)  NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_media_tree_status (tree_id, status, tombstoned_at),
    KEY ix_media_album (album_id, tombstoned_at),
    KEY ix_media_sha (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE album (
    id              CHAR(36)      NOT NULL,
    tree_id         CHAR(36)      NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    description     TEXT          NULL,
    cover_media_id  CHAR(36)      NULL,
    created_at      TIMESTAMP(6)  NOT NULL,
    updated_at      TIMESTAMP(6)  NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    tombstoned_at   TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_album_tree (tree_id, tombstoned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE media_quarantine (
    media_id        CHAR(36)      NOT NULL,
    tree_id         CHAR(36)      NOT NULL,
    sha256          CHAR(64)      NOT NULL,
    byte_size       BIGINT        NOT NULL,
    quarantine_path VARCHAR(1024) NOT NULL,
    scanner_verdict VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    infected        BOOLEAN       NOT NULL DEFAULT FALSE,
    recorded_at     TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (media_id),
    KEY ix_quarantine_verdict (scanner_verdict, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE media_thumbnail (
    media_id        CHAR(36)      NOT NULL,
    tree_id         CHAR(36)      NOT NULL,
    width           INT           NOT NULL,
    height          INT           NOT NULL,
    mime_type       VARCHAR(64)   NOT NULL,
    quarantine_path VARCHAR(1024) NOT NULL,
    sha256          CHAR(64)      NOT NULL,
    generated_at    TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (media_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE media_reference (
    media_id        CHAR(36)     NOT NULL,
    tree_id         CHAR(36)     NOT NULL,
    target_kind     VARCHAR(16)  NOT NULL,
    target_id       CHAR(36)     NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    last_attempt_at TIMESTAMP(6) NULL,
    last_error_code VARCHAR(64)  NULL,
    PRIMARY KEY (media_id, target_kind, target_id),
    KEY ix_reference_target (target_kind, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE media_retention_hold (
    id              CHAR(36)      NOT NULL,
    media_id        CHAR(36)      NOT NULL,
    tree_id         CHAR(36)      NOT NULL,
    hold_until      TIMESTAMP(6)  NOT NULL,
    reason          VARCHAR(128)  NOT NULL,
    released_at     TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_retention_pending (hold_until, released_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE media_binary_replication (
    id              CHAR(36)     NOT NULL,
    media_id        CHAR(36)     NOT NULL,
    source_region   VARCHAR(32)  NOT NULL,
    target_region   VARCHAR(32)  NOT NULL,
    sha256          CHAR(64)     NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    last_attempt_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY ix_replication_pending (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE media_watermark (
    tree_id      CHAR(36)     NOT NULL,
    domain       VARCHAR(64)  NOT NULL,
    watermark    BIGINT       NOT NULL,
    last_updated TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outbox_record (
    id                 CHAR(36)      NOT NULL,
    aggregate_type     VARCHAR(64)   NOT NULL,
    aggregate_id       VARCHAR(64)   NOT NULL,
    aggregate_version  BIGINT        NOT NULL,
    event_type         VARCHAR(128)  NOT NULL,
    event_version      INT           NOT NULL,
    topic              VARCHAR(128)  NOT NULL,
    partition_key      VARCHAR(128)  NOT NULL,
    correlation_id     CHAR(36)      NULL,
    causation_id       CHAR(36)      NULL,
    operation_id       CHAR(36)      NULL,
    traceparent        VARCHAR(64)   NULL,
    payload_json       JSON          NOT NULL,
    headers_json       JSON          NULL,
    occurred_at        TIMESTAMP(6)  NOT NULL,
    locked_until       TIMESTAMP(6)  NULL,
    published_at       TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    KEY ix_outbox_unpublished (published_at, locked_until, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inbox_record (
    event_id     CHAR(36)      NOT NULL,
    consumer     VARCHAR(64)   NOT NULL,
    topic        VARCHAR(128)  NOT NULL,
    partition_no INT           NOT NULL,
    offset_no    BIGINT        NOT NULL,
    consumed_at  TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (event_id, consumer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(128) NOT NULL,
    service_name    VARCHAR(64)  NOT NULL,
    payload_hash    CHAR(64)     NOT NULL,
    operation_id    CHAR(36)     NOT NULL,
    response_body   JSON         NOT NULL,
    recorded_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (idempotency_key, service_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE operation_audit (
    id              CHAR(36)      NOT NULL,
    correlation_id  CHAR(36)      NULL,
    service         VARCHAR(64)   NOT NULL,
    operation_type  VARCHAR(128)  NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    detail_json     JSON          NULL,
    started_at      TIMESTAMP(6)  NOT NULL,
    finished_at     TIMESTAMP(6)  NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE authorization_projection (
    tree_id          CHAR(36)     NOT NULL,
    user_id          CHAR(36)     NOT NULL,
    role             VARCHAR(16)  NULL,
    revision         BIGINT       NOT NULL,
    epoch            BIGINT       NOT NULL,
    granted_at       TIMESTAMP(6) NOT NULL,
    revoked          BOOLEAN      NOT NULL DEFAULT FALSE,
    source_event_id  CHAR(36)     NULL,
    last_updated_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE member_reference_projection (
    tree_id    CHAR(36)     NOT NULL,
    member_id  CHAR(36)     NOT NULL,
    exists     BOOLEAN      NOT NULL,
    tombstoned BOOLEAN      NOT NULL DEFAULT FALSE,
    last_updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE event_reference_projection (
    tree_id    CHAR(36)     NOT NULL,
    event_id   CHAR(36)     NOT NULL,
    exists     BOOLEAN      NOT NULL,
    tombstoned BOOLEAN      NOT NULL DEFAULT FALSE,
    last_updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE replay_ledger (
    aggregate_id        CHAR(36)     NOT NULL,
    topic               VARCHAR(128) NOT NULL,
    partition_no        INT          NOT NULL,
    last_seen_offset    BIGINT       NOT NULL,
    aggregate_revision  BIGINT       NOT NULL,
    epoch               BIGINT       NOT NULL,
    recorded_at         TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (aggregate_id, topic, partition_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
