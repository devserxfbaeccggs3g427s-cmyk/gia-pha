CREATE TABLE family_trees (
    tree_key                BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    owner_user_key          BIGINT UNSIGNED     NOT NULL,
    name                    VARCHAR(200)        NOT NULL,
    description             VARCHAR(2000)       NULL,
    revision                BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (tree_key),
    UNIQUE KEY uk_family_trees_external_id (external_id),
    KEY ix_family_trees_owner (owner_user_key)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE tree_memberships (
    tree_key                BIGINT UNSIGNED     NOT NULL,
    user_key                BIGINT UNSIGNED     NOT NULL,
    role                    VARCHAR(10)         NOT NULL,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (tree_key, user_key),
    KEY ix_tree_memberships_user_tree (user_key, tree_key),
    CONSTRAINT fk_tree_memberships_tree
        FOREIGN KEY (tree_key) REFERENCES family_trees (tree_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_tree_memberships_role
        CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;