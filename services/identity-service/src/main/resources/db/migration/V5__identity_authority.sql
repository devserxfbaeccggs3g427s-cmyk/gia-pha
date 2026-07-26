-- ---------------------------------------------------------------------------
-- V5: Global identity authority state machine (Task 20.3, ADR-009).
--
-- A single-row table (enforced by a CHECK) records the current identity
-- writer for the whole platform:
--
--   * LEGACY    — only the Next.js/Blob adapter may mutate identity data.
--   * TRANSITION — both writers are technically available but a freeze is in
--                  force so the legacy store cannot be written to; the
--                  MySQL-backed compatibility adapter (Task 20.2) may write.
--   * SPRING    — only the Spring/MySQL identity path may mutate identity.
--
-- A switch is atomic (single-row UPDATE … version = :expected). Producers
-- and the bridge check this table on every identity write/issue; a reader
-- attempting the wrong action gets `IDENTITY_WRITER_FORBIDDEN`.
-- ---------------------------------------------------------------------------

CREATE TABLE identity_authority (
    authority_id           TINYINT UNSIGNED     NOT NULL,
    writer                 VARCHAR(15)          NOT NULL,
    freeze                 TINYINT(1)           NOT NULL DEFAULT 0,
    allow_legacy_reads     TINYINT(1)           NOT NULL DEFAULT 1,
    allow_legacy_writes    TINYINT(1)           NOT NULL DEFAULT 1,
    allow_spring_reads     TINYINT(1)           NOT NULL DEFAULT 1,
    allow_spring_writes    TINYINT(1)           NOT NULL DEFAULT 1,
    last_switch_at         DATETIME(6)          NULL,
    last_switch_reason     VARCHAR(200)         NULL,
    version                BIGINT UNSIGNED      NOT NULL DEFAULT 1,
    updated_at             DATETIME(6)          NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_at             DATETIME(6)          NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (authority_id),
    CONSTRAINT ck_identity_authority_singleton
        CHECK (authority_id = 1),
    CONSTRAINT ck_identity_authority_writer
        CHECK (writer IN ('LEGACY', 'TRANSITION', 'SPRING'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

INSERT INTO identity_authority
    (authority_id, writer, freeze, allow_legacy_reads, allow_legacy_writes,
     allow_spring_reads, allow_spring_writes)
VALUES
    (1, 'LEGACY', 0, 1, 1, 1, 0);
