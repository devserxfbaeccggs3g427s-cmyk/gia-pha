CREATE TABLE relationships (
    relationship_key        BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    tree_key                BIGINT UNSIGNED     NOT NULL,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source_member_key       BIGINT UNSIGNED     NOT NULL,
    target_member_key       BIGINT UNSIGNED     NOT NULL,
    relation_type           VARCHAR(20)         NOT NULL,
    custom_type             VARCHAR(100)        NULL,
    marriage_date           DATE                NULL,
    divorce_date            DATE                NULL,
    marriage_status         VARCHAR(10)         NULL,
    -- Canonical (direction-independent) pair keys backing the unique logical
    -- relationship key; STORED so they can be indexed.
    pair_low_member_key     BIGINT UNSIGNED
        GENERATED ALWAYS AS (LEAST(source_member_key, target_member_key)) STORED,
    pair_high_member_key    BIGINT UNSIGNED
        GENERATED ALWAYS AS (GREATEST(source_member_key, target_member_key)) STORED,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (relationship_key),
    UNIQUE KEY uk_relationships_tree_external (tree_key, external_id),
    UNIQUE KEY uk_relationships_logical
        (tree_key, relation_type, pair_low_member_key, pair_high_member_key),
    KEY ix_relationships_tree_source (tree_key, source_member_key),
    KEY ix_relationships_tree_target (tree_key, target_member_key),
    CONSTRAINT fk_relationships_tree
        FOREIGN KEY (tree_key) REFERENCES family_trees (tree_key)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_relationships_source_same_tree
        FOREIGN KEY (tree_key, source_member_key)
        REFERENCES members (tree_key, member_key)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_relationships_target_same_tree
        FOREIGN KEY (tree_key, target_member_key)
        REFERENCES members (tree_key, member_key)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_relationships_type
        CHECK (relation_type IN ('PARENT_CHILD', 'SPOUSE', 'SIBLING', 'ADOPTED', 'CUSTOM')),
    CONSTRAINT ck_relationships_no_self
        CHECK (source_member_key <> target_member_key),
    CONSTRAINT ck_relationships_custom_type_required
        CHECK (relation_type <> 'CUSTOM' OR custom_type IS NOT NULL),
    CONSTRAINT ck_relationships_marriage_status
        CHECK (marriage_status IS NULL
               OR marriage_status IN ('MARRIED', 'DIVORCED', 'WIDOWED'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;