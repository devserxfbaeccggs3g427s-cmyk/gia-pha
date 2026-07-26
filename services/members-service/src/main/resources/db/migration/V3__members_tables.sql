CREATE TABLE members (
    member_key              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    tree_key                BIGINT UNSIGNED     NOT NULL,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    first_name              VARCHAR(100)        NOT NULL,
    last_name               VARCHAR(100)        NOT NULL,
    full_name               VARCHAR(200)        NOT NULL,
    nickname                VARCHAR(100)        NULL,
    gender                  VARCHAR(10)         NOT NULL,
    date_of_birth           DATE                NULL,
    date_of_death           DATE                NULL,
    place_of_birth          VARCHAR(200)        NULL,
    current_address         VARCHAR(500)        NULL,
    phone                   VARCHAR(20)         NULL,
    email                   VARCHAR(254)        NULL,
    occupation              VARCHAR(200)        NULL,
    education               VARCHAR(200)        NULL,
    biography               VARCHAR(5000)       NULL,
    achievements            VARCHAR(2000)       NULL,
    notes                   VARCHAR(2000)       NULL,
    legacy_avatar_url       VARCHAR(2048)       NULL,
    generation              SMALLINT UNSIGNED   NULL,
    is_alive                TINYINT(1)          NOT NULL DEFAULT 1,
    -- Vietnamese search-normalized projections; maintained in the same
    -- transaction as the source columns (design.md, Requirement 14).
    full_name_search        VARCHAR(200)        NOT NULL,
    nickname_search         VARCHAR(100)        NULL,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (member_key),
    UNIQUE KEY uk_members_tree_external (tree_key, external_id),
    UNIQUE KEY uk_members_tree_member (tree_key, member_key),
    KEY ix_members_tree_full_name_search (tree_key, full_name_search),
    KEY ix_members_tree_last_first (tree_key, last_name, first_name),
    KEY ix_members_tree_generation (tree_key, generation),
    KEY ix_members_tree_alive (tree_key, is_alive),
    KEY ix_members_tree_birth (tree_key, date_of_birth),
    KEY ix_members_tree_death (tree_key, date_of_death),
    CONSTRAINT fk_members_tree
        FOREIGN KEY (tree_key) REFERENCES family_trees (tree_key)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_members_gender
        CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT ck_members_death_not_before_birth
        CHECK (date_of_death IS NULL OR date_of_birth IS NULL
               OR date_of_death >= date_of_birth),
    CONSTRAINT ck_members_alive_without_death
        CHECK (is_alive = 0 OR date_of_death IS NULL)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;