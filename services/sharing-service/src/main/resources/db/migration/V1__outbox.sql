-- Per-service outbox table. Per Requirement 2.3 ("Outbox writes SHALL be
-- appended in the same local transaction as the domain change") and
-- Requirement 2.4 ("The relay SHALL publish outbox events at-least-once").
--
-- Each service owns and migrates its own copy of this schema.

CREATE TABLE outbox (
    id              CHAR(36) PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    VARCHAR(64) NOT NULL,
    tree_key        BIGINT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload_json    JSON NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    published_at    TIMESTAMP(6) NULL,
    attempts        INT NOT NULL DEFAULT 0,
    signature       VARCHAR(512) NULL,
    INDEX idx_outbox_unpublished (published_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
