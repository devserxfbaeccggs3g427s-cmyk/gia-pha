-- V1__init.sql — sharing-service schema authority (ADR-002, ADR-009).
--
-- Sharing owns hashed links, expiry/revocation, and allowlisted public
-- projections. Public projection rebuilds happen from per-domain
-- projections, never by joining across services.

CREATE TABLE share_link (
    id                  CHAR(36)      NOT NULL,
    tree_id             CHAR(36)      NOT NULL,
    scope               VARCHAR(16)   NOT NULL,
    target_id           CHAR(36)      NULL,
    role                VARCHAR(16)   NOT NULL,
    token_hash          CHAR(64)      NOT NULL,
    created_by_user_id  CHAR(36)      NOT NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    expires_at          TIMESTAMP(6)  NULL,
    revoked_at          TIMESTAMP(6)  NULL,
    revocation_reason   VARCHAR(256)  NULL,
    revision            BIGINT        NOT NULL DEFAULT 0,
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_share_token (token_hash),
    KEY ix_share_tree (tree_id, created_at),
    KEY ix_share_target (scope, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_allowlisted_projection (
    tree_id          CHAR(36)      NOT NULL,
    scope            VARCHAR(16)   NOT NULL,
    target_id        CHAR(36)      NULL,
    allowlisted_json JSON          NOT NULL,
    watermark        BIGINT        NOT NULL,
    last_updated     TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, scope, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_watermark (
    tree_id      CHAR(36)     NOT NULL,
    domain       VARCHAR(64)  NOT NULL,
    watermark    BIGINT       NOT NULL,
    last_updated TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_member_projection (
    tree_id        CHAR(36)      NOT NULL,
    member_id      CHAR(36)      NOT NULL,
    allowlisted    JSON          NOT NULL,
    tombstoned     BOOLEAN       NOT NULL DEFAULT FALSE,
    last_updated   TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_media_projection (
    tree_id        CHAR(36)      NOT NULL,
    media_id       CHAR(36)      NOT NULL,
    allowlisted    JSON          NOT NULL,
    tombstoned     BOOLEAN       NOT NULL DEFAULT FALSE,
    last_updated   TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, media_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_event_projection (
    tree_id        CHAR(36)      NOT NULL,
    event_id       CHAR(36)      NOT NULL,
    allowlisted    JSON          NOT NULL,
    tombstoned     BOOLEAN       NOT NULL DEFAULT FALSE,
    last_updated   TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_tree_projection (
    tree_id        CHAR(36)      NOT NULL,
    allowlisted    JSON          NOT NULL,
    tombstoned     BOOLEAN       NOT NULL DEFAULT FALSE,
    last_updated   TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_relationship_projection (
    tree_id        CHAR(36)      NOT NULL,
    relationship_id CHAR(36)     NOT NULL,
    allowlisted    JSON          NOT NULL,
    tombstoned     BOOLEAN       NOT NULL DEFAULT FALSE,
    last_updated   TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, relationship_id)
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
