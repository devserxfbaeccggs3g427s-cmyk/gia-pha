-- V1__init.sql — event-service schema authority (ADR-002, ADR-009).
--
-- Event owns CRUD, recurrence, deterministic ordering, leap-day
-- behaviour, and local member/media references without cross-service
-- foreign keys. References to members and media are local opaque IDs
-- validated against the projections.

CREATE TABLE domain_event (
    id              CHAR(36)      NOT NULL,
    tree_id         CHAR(36)      NOT NULL,
    title           VARCHAR(255)  NOT NULL,
    description     TEXT          NULL,
    kind            VARCHAR(16)   NOT NULL,
    start_date      DATE          NULL,
    end_date        DATE          NULL,
    -- Recurrence: rrule follows RFC 5545 with v1 subset
    -- (DAILY/WEEKLY/MONTHLY/YEARLY, COUNT, UNTIL).
    recurrence_json JSON          NULL,
    -- Local member references; validated against the member projection.
    -- No cross-service foreign keys.
    primary_member_id CHAR(36)    NULL,
    additional_member_ids JSON    NULL,
    media_refs      JSON          NULL,
    location        VARCHAR(512)  NULL,
    revision        BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6)  NOT NULL,
    updated_at      TIMESTAMP(6)  NOT NULL,
    tombstoned_at   TIMESTAMP(6)  NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_event_tree (tree_id, tombstoned_at, start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V2__outbox_inbox_projection.sql

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
    id                 CHAR(36)      NOT NULL,
    correlation_id     CHAR(36)      NULL,
    service            VARCHAR(64)   NOT NULL,
    operation_type     VARCHAR(128)  NOT NULL,
    status             VARCHAR(32)   NOT NULL,
    detail_json        JSON          NULL,
    started_at         TIMESTAMP(6)  NOT NULL,
    finished_at        TIMESTAMP(6)  NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Authorization projection (consumed from tree.memberships.v1).
CREATE TABLE authorization_projection (
    tree_id              CHAR(36)     NOT NULL,
    user_id              CHAR(36)     NOT NULL,
    role                 VARCHAR(16)  NULL,
    revision             BIGINT       NOT NULL,
    epoch                BIGINT       NOT NULL,
    granted_at           TIMESTAMP(6) NOT NULL,
    revoked              BOOLEAN      NOT NULL DEFAULT FALSE,
    source_event_id      CHAR(36)     NULL,
    last_updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Local projections for cross-domain references. The projections are
-- fed by the Member and Media services via Kafka. References in
-- domain_event are kept even when the projection row is missing —
-- reads mark them dangling and surface the condition so the caller
-- can reconcile.
CREATE TABLE member_reference_projection (
    tree_id        CHAR(36)     NOT NULL,
    member_id      CHAR(36)     NOT NULL,
    exists         BOOLEAN      NOT NULL,
    tombstoned     BOOLEAN      NOT NULL DEFAULT FALSE,
    last_updated   TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE media_reference_projection (
    tree_id        CHAR(36)     NOT NULL,
    media_id       CHAR(36)     NOT NULL,
    exists         BOOLEAN      NOT NULL,
    tombstoned     BOOLEAN      NOT NULL DEFAULT FALSE,
    last_updated   TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, media_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE replay_ledger (
    aggregate_id         CHAR(36)     NOT NULL,
    topic                VARCHAR(128) NOT NULL,
    partition_no         INT          NOT NULL,
    last_seen_offset     BIGINT       NOT NULL,
    aggregate_revision   BIGINT       NOT NULL,
    epoch                BIGINT       NOT NULL,
    recorded_at          TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (aggregate_id, topic, partition_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;