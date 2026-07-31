-- V1__init.sql — relationship-service schema authority (ADR-002, ADR-009).
--
-- Relationship owns the genealogy graph: canonical keys, cycle
-- validation, generation/ancestry/spouse/adoption algorithms. Graph
-- commands are serialized by treeId partition and aggregate version
-- (Task 8.2). References to members are local opaque IDs.

CREATE TABLE relationship (
    id                CHAR(36)      NOT NULL,
    tree_id           CHAR(36)      NOT NULL,
    kind              VARCHAR(16)   NOT NULL,
    from_member_id    CHAR(36)      NOT NULL,
    to_member_id      CHAR(36)      NOT NULL,
    -- For PARENT_CHILD this is the parent; for SPOUSE both rows are
    -- inserted symmetrically; for ADOPTION the adopter.
    metadata_json     JSON          NULL,
    revision          BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP(6)  NOT NULL,
    tombstoned_at     TIMESTAMP(6)  NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- Canonical logical key: a tree cannot have two PARENT_CHILD
    -- edges with the same (kind, parent, child); SPOUSE edges are
    -- symmetric and use an unordered pair; ADOPTION uses adopter/child.
    UNIQUE KEY uq_relationship_canonical (tree_id, kind, from_member_id, to_member_id),
    KEY ix_relationship_tree (tree_id, tombstoned_at),
    KEY ix_relationship_child (tree_id, to_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Graph command log — every accepted command writes a row here so the
-- orchestrator can replay/serialize by (treeId, version). This is the
-- aggregate-version checkpoint for the per-tree graph serializer.
CREATE TABLE graph_command_log (
    tree_id         CHAR(36)      NOT NULL,
    command_seq     BIGINT        NOT NULL,
    command_type    VARCHAR(32)   NOT NULL,
    actor_user_id   CHAR(36)      NOT NULL,
    payload_hash    CHAR(64)      NOT NULL,
    committed_at    TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (tree_id, command_seq)
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

-- Member existence projection: consumed from member.events.v1.
-- When a member is tombstoned we mark references as "dangling" so
-- the relationship aggregate can refuse new edges but keep the
-- historical record for the family graph view.
CREATE TABLE member_existence_projection (
    tree_id        CHAR(36)     NOT NULL,
    member_id      CHAR(36)     NOT NULL,
    exists         BOOLEAN      NOT NULL,
    tombstoned     BOOLEAN      NOT NULL DEFAULT FALSE,
    last_updated   TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tree_id, member_id)
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