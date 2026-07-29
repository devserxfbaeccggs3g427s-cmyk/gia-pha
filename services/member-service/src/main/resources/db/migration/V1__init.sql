-- V1__init.sql — member-service schema authority (ADR-002, ADR-009).
--
-- Member owns profile, lifespan/status validation, duplicate/merge,
-- tombstones, and read-only legacy avatar URL fallback. References
-- to users live in identity (foreign key prohibition) — we store an
-- opaque user_id UUID.

CREATE TABLE member (
    id                  CHAR(36)      NOT NULL,
    tree_id             CHAR(36)      NOT NULL,
    user_id             CHAR(36)      NULL,
    display_name        VARCHAR(255)  NOT NULL,
    given_name          VARCHAR(255)  NULL,
    surname             VARCHAR(255)  NULL,
    birth_date          DATE          NULL,
    death_date          DATE          NULL,
    birth_year_known    BOOLEAN       NOT NULL DEFAULT FALSE,
    death_year_known    BOOLEAN       NOT NULL DEFAULT FALSE,
    gender              VARCHAR(16)   NULL,
    status              VARCHAR(16)   NOT NULL,
    generation          INT           NULL,
    legacy_avatar_url   VARCHAR(1024) NULL,
    notes               TEXT          NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    tombstoned_at       TIMESTAMP(6)  NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_member_tree_status (tree_id, status, tombstoned_at),
    KEY ix_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Canonical dedup key: a person is uniquely identified by (tree_id,
-- given_name, surname, birth_date). NULL birth_date is handled by a
-- unique key variant on (tree_id, given_name, surname) for matches
-- with unknown birth date.
CREATE TABLE member_canonical_key (
    tree_id     CHAR(36)      NOT NULL,
    given_name  VARCHAR(255)  NOT NULL,
    surname     VARCHAR(255)  NOT NULL,
    birth_date  DATE          NULL,
    member_id   CHAR(36)      NOT NULL,
    PRIMARY KEY (tree_id, given_name, surname, birth_date),
    KEY ix_canonical_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V2__outbox.sql — transactional outbox, inbox, idempotency, and the
-- authorization projection table (the Member service's local copy of
-- the membership stream).

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

-- Local authorization projection. The Member service consumes
-- `tree.memberships.v1` into this table. Authorization decisions are
-- made locally from this projection; a stale or missing row blocks
-- unsafe mutations.
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

-- Replay/reconciliation ledger.
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