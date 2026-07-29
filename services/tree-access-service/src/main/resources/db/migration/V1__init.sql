-- V1__init.sql — tree-access-service schema authority (ADR-002, ADR-009).
--
-- Tree Access owns trees, ownership, memberships, RBAC, lifecycle, and
-- the authoritative tree revision/epoch. Domain services consume the
-- ordered membership stream into their local authorization projections.

CREATE TABLE tree (
    id              CHAR(36)      NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    owner_user_id   CHAR(36)      NOT NULL,
    state           VARCHAR(16)   NOT NULL,
    revision        BIGINT        NOT NULL DEFAULT 0,
    epoch           BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6)  NOT NULL,
    frozen_at       TIMESTAMP(6)  NULL,
    tombstoned_at   TIMESTAMP(6)  NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_tree_owner (owner_user_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tree_membership (
    id              CHAR(36)      NOT NULL,
    tree_id         CHAR(36)      NOT NULL,
    user_id         CHAR(36)      NOT NULL,
    role            VARCHAR(16)   NOT NULL,
    granted_by      CHAR(36)      NOT NULL,
    granted_at      TIMESTAMP(6)  NOT NULL,
    revoked_at      TIMESTAMP(6)  NULL,
    revoked_by      CHAR(36)      NULL,
    revocation_reason VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_membership_active (tree_id, user_id, revoked_at),
    KEY ix_membership_user (user_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tree_revision_audit (
    tree_id         CHAR(36)      NOT NULL,
    revision        BIGINT        NOT NULL,
    epoch           BIGINT        NOT NULL,
    advanced_by     CHAR(36)      NOT NULL,
    reason          VARCHAR(64)   NOT NULL,
    advanced_at     TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V2__outbox.sql — transactional outbox, inbox, idempotency, and
-- authorization projection tables. The projection is the read model
-- the local emergency gRPC lookup answers from when a deadline-bound
-- call comes in (ADR-005).

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

-- Authorization projection table — Tree Access is authoritative. Other
-- services consume `tree.memberships.v1` into their own local copy of
-- this projection, but this row is the source of truth for the
-- emergency gRPC lookup and the cutover compare-and-set.
CREATE TABLE authorization_projection (
    tree_id              CHAR(36)     NOT NULL,
    user_id              CHAR(36)     NOT NULL,
    role                 VARCHAR(16)  NOT NULL,
    revision             BIGINT       NOT NULL,
    epoch                BIGINT       NOT NULL,
    granted_at           TIMESTAMP(6) NOT NULL,
    revoked              BOOLEAN      NOT NULL DEFAULT FALSE,
    source_event_id      CHAR(36)     NULL,
    last_updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, user_id)
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