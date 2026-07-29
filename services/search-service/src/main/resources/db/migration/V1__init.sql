-- V1__init.sql — search-service schema authority (ADR-002, ADR-009).
--
-- Search & Reporting owns MySQL read models for Vietnamese search,
-- autocomplete, statistics, and reports. Coherent outputs require
-- revision barriers tracked in the watermark table.

CREATE TABLE search_member_doc (
    tree_id         CHAR(36)      NOT NULL,
    member_id       CHAR(36)      NOT NULL,
    full_name       VARCHAR(512)  NOT NULL,
    given_name      VARCHAR(255)  NULL,
    surname         VARCHAR(255)  NULL,
    birth_year      INT           NULL,
    death_year      INT           NULL,
    tombstoned      BOOLEAN       NOT NULL DEFAULT FALSE,
    normalized_name VARCHAR(512)  NOT NULL,
    last_updated    TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, member_id),
    KEY ix_member_name (tree_id, normalized_name),
    KEY ix_member_birth (tree_id, birth_year, tombstoned)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE search_event_doc (
    tree_id         CHAR(36)      NOT NULL,
    event_id        CHAR(36)      NOT NULL,
    title           VARCHAR(512)  NOT NULL,
    start_date      DATE          NULL,
    kind            VARCHAR(32)   NULL,
    tombstoned      BOOLEAN       NOT NULL DEFAULT FALSE,
    normalized_title VARCHAR(512) NOT NULL,
    last_updated    TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, event_id),
    KEY ix_event_title (tree_id, normalized_title),
    KEY ix_event_date (tree_id, start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE search_media_doc (
    tree_id           CHAR(36)      NOT NULL,
    media_id          CHAR(36)      NOT NULL,
    filename          VARCHAR(512)  NOT NULL,
    kind              VARCHAR(32)   NOT NULL,
    tombstoned        BOOLEAN       NOT NULL DEFAULT FALSE,
    normalized_filename VARCHAR(512) NOT NULL,
    last_updated      TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, media_id),
    KEY ix_media_filename (tree_id, normalized_filename)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE autocomplete_entry (
    tree_id            CHAR(36)      NOT NULL,
    owner_id           CHAR(36)      NULL,
    surface            VARCHAR(255)  NOT NULL,
    normalized_prefix  VARCHAR(255)  NOT NULL,
    weight             INT           NOT NULL DEFAULT 0,
    last_updated       TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, normalized_prefix, surface),
    KEY ix_autocomplete_prefix (tree_id, normalized_prefix)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE statistics_snapshot (
    tree_id      CHAR(36)     NOT NULL,
    member_count BIGINT       NOT NULL,
    generations  INT          NOT NULL,
    events_count BIGINT       NOT NULL,
    media_count  BIGINT       NOT NULL,
    watermark    BIGINT       NOT NULL,
    computed_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id),
    KEY ix_statistics_watermark (tree_id, watermark)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE report_snapshot (
    id          CHAR(36)     NOT NULL,
    tree_id     CHAR(36)     NOT NULL,
    kind        VARCHAR(32)  NOT NULL,
    payload     JSON         NOT NULL,
    watermark   BIGINT       NOT NULL,
    computed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_report_kind_watermark (tree_id, kind, watermark),
    KEY ix_report_tree (tree_id, kind, watermark)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE member_generation_projection (
    tree_id     CHAR(36) NOT NULL,
    member_id   CHAR(36) NOT NULL,
    generation  INT      NOT NULL,
    tombstoned  BOOLEAN  NOT NULL DEFAULT FALSE,
    last_updated TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE watermark (
    tree_id      CHAR(36)     NOT NULL,
    domain       VARCHAR(64)  NOT NULL,
    value        BIGINT       NOT NULL,
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
    event_id    CHAR(36)      NOT NULL,
    consumer    VARCHAR(64)   NOT NULL,
    topic       VARCHAR(128)  NOT NULL,
    partition_no INT          NOT NULL,
    offset_no   BIGINT        NOT NULL,
    consumed_at TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (event_id, consumer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE idempotency_record (
    idempotency_key  VARCHAR(128) NOT NULL,
    service_name     VARCHAR(64)  NOT NULL,
    payload_hash     CHAR(64)     NOT NULL,
    operation_id     CHAR(36)     NOT NULL,
    response_body    JSON         NOT NULL,
    recorded_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
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
