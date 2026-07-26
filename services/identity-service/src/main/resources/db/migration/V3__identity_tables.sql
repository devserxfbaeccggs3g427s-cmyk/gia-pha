CREATE TABLE users (
    user_key                BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    email                   VARCHAR(254)        NOT NULL,
    name                    VARCHAR(200)        NOT NULL,
    password_hash           VARCHAR(255)        NULL,
    image_url               VARCHAR(2048)       NULL,
    provider                VARCHAR(20)         NOT NULL DEFAULT 'credentials',
    email_verified_at       DATETIME(6)         NULL,
    failed_login_attempts   SMALLINT UNSIGNED   NOT NULL DEFAULT 0,
    locked_until            DATETIME(6)         NULL,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    deleted_at              DATETIME(6)         NULL,
    PRIMARY KEY (user_key),
    UNIQUE KEY uk_users_external_id (external_id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT ck_users_provider
        CHECK (provider IN ('credentials', 'google', 'facebook')),
    CONSTRAINT ck_users_email_normalized
        CHECK (CAST(email AS BINARY) = CAST(LOWER(email) AS BINARY))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE oauth_accounts (
    oauth_account_key       BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_key                BIGINT UNSIGNED     NOT NULL,
    provider                VARCHAR(20)         NOT NULL,
    provider_account_id     VARCHAR(255)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (oauth_account_key),
    UNIQUE KEY uk_oauth_accounts_provider_account (provider, provider_account_id),
    KEY ix_oauth_accounts_user (user_key),
    CONSTRAINT fk_oauth_accounts_user
        FOREIGN KEY (user_key) REFERENCES users (user_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_oauth_accounts_provider
        CHECK (provider IN ('google', 'facebook'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE verification_tokens (
    verification_token_key  BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    user_key                BIGINT UNSIGNED     NOT NULL,
    purpose                 VARCHAR(40)         NOT NULL DEFAULT 'EMAIL_VERIFICATION',
    token_hash              BINARY(32)          NOT NULL,
    expires_at              DATETIME(6)         NOT NULL,
    consumed_at             DATETIME(6)         NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (verification_token_key),
    UNIQUE KEY uk_verification_tokens_hash (token_hash),
    KEY ix_verification_tokens_user_purpose (user_key, purpose, expires_at),
    CONSTRAINT fk_verification_tokens_user
        FOREIGN KEY (user_key) REFERENCES users (user_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_verification_tokens_purpose
        CHECK (purpose IN ('EMAIL_VERIFICATION'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE auth_sessions (
    session_key             BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    user_key                BIGINT UNSIGNED     NOT NULL,
    token_hash              BINARY(32)          NOT NULL,
    issued_at               DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    last_seen_at            DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    idle_expires_at         DATETIME(6)         NOT NULL,
    absolute_expires_at     DATETIME(6)         NOT NULL,
    rotated_from_session_key BIGINT UNSIGNED    NULL,
    revoked_at              DATETIME(6)         NULL,
    revoke_reason           VARCHAR(100)        NULL,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (session_key),
    UNIQUE KEY uk_auth_sessions_external_id (external_id),
    UNIQUE KEY uk_auth_sessions_token_hash (token_hash),
    KEY ix_auth_sessions_user_expiry (user_key, absolute_expires_at),
    KEY ix_auth_sessions_expiry_sweep (absolute_expires_at),
    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_key) REFERENCES users (user_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_auth_sessions_rotated_from
        FOREIGN KEY (rotated_from_session_key) REFERENCES auth_sessions (session_key)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT ck_auth_sessions_expiry_order
        CHECK (idle_expires_at <= absolute_expires_at)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;