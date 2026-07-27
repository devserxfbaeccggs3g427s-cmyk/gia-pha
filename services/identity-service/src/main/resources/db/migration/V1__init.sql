-- V1__init.sql — Identity schema authority (ADR-002, ADR-009).
--
-- Flyway is the schema authority. jOOQ generation reads this schema
-- to produce the typed query DSL. Any change MUST be a new migration.

CREATE TABLE users (
    id                  CHAR(36)      NOT NULL,
    normalized_email    VARCHAR(320)  NOT NULL,
    bcrypt_hash         VARCHAR(80)   NOT NULL,
    verification_state  VARCHAR(16)   NOT NULL,
    locked              BOOLEAN       NOT NULL DEFAULT FALSE,
    locked_until        TIMESTAMP(6)  NULL,
    failed_attempts     INT           NOT NULL DEFAULT 0,
    created_at          TIMESTAMP(6)  NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (normalized_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE session (
    id                  CHAR(36)      NOT NULL,
    user_id             CHAR(36)      NOT NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    absolute_expires_at TIMESTAMP(6)  NOT NULL,
    idle_expires_at     TIMESTAMP(6)  NOT NULL,
    user_agent          VARCHAR(512)  NULL,
    ip_hash             VARCHAR(64)   NULL,
    revoked             BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY ix_session_user (user_id, revoked, absolute_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oauth_link (
    id                  CHAR(36)      NOT NULL,
    user_id             CHAR(36)      NOT NULL,
    provider            VARCHAR(32)   NOT NULL,
    provider_subject    VARCHAR(255)  NOT NULL,
    normalized_email    VARCHAR(320)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_oauth_provider_subject (provider, provider_subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE registration_attempt (
    ip_address          VARCHAR(64)   NOT NULL,
    bucket_hour         TIMESTAMP(6)  NOT NULL,
    attempted_at        TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (ip_address, bucket_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE verification_token (
    token               CHAR(36)      NOT NULL,
    user_id             CHAR(36)      NOT NULL,
    issued_at           TIMESTAMP(6)  NOT NULL,
    expires_at          TIMESTAMP(6)  NOT NULL,
    consumed_at         TIMESTAMP(6)  NULL,
    PRIMARY KEY (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
