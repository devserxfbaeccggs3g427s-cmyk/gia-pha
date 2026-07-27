-- V1__init.sql — event-service schema authority (ADR-002, ADR-009).
-- Replace the placeholder tables below with the real schema.
CREATE TABLE event_placeholder (
    id            CHAR(36)     NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
