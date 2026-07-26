CREATE TABLE events (
    event_key               BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    tree_key                BIGINT UNSIGNED     NOT NULL,
    external_id             VARCHAR(300)        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    event_type              VARCHAR(20)         NOT NULL,
    custom_type             VARCHAR(100)        NULL,
    title                   VARCHAR(200)        NOT NULL,
    event_date              DATE                NOT NULL,
    location                VARCHAR(300)        NULL,
    description             VARCHAR(2000)       NULL,
    version                 BIGINT UNSIGNED     NOT NULL DEFAULT 1,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (event_key),
    UNIQUE KEY uk_events_tree_external (tree_key, external_id),
    UNIQUE KEY uk_events_tree_event (tree_key, event_key),
    KEY ix_events_tree_date (tree_key, event_date),
    KEY ix_events_tree_type (tree_key, event_type),
    CONSTRAINT fk_events_tree
        FOREIGN KEY (tree_key) REFERENCES family_trees (tree_key)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_events_type
        CHECK (event_type IN ('BIRTHDAY', 'WEDDING', 'FUNERAL', 'REUNION',
                              'ANNIVERSARY', 'CUSTOM')),
    CONSTRAINT ck_events_custom_type_required
        CHECK (event_type <> 'CUSTOM' OR custom_type IS NOT NULL)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE event_members (
    tree_key                BIGINT UNSIGNED     NOT NULL,
    event_key               BIGINT UNSIGNED     NOT NULL,
    member_key              BIGINT UNSIGNED     NOT NULL,
    position                INT UNSIGNED        NOT NULL DEFAULT 0,
    created_at              DATETIME(6)         NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (tree_key, event_key, member_key),
    KEY ix_event_members_member (tree_key, member_key),
    CONSTRAINT fk_event_members_event_same_tree
        FOREIGN KEY (tree_key, event_key)
        REFERENCES events (tree_key, event_key)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_event_members_member_same_tree
        FOREIGN KEY (tree_key, member_key)
        REFERENCES members (tree_key, member_key)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;