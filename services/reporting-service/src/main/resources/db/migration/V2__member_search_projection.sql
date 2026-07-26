CREATE TABLE member_search_projection (
    member_key BIGINT UNSIGNED NOT NULL,
    external_id VARCHAR(300) NOT NULL,
    tree_key BIGINT UNSIGNED NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    full_name_search VARCHAR(255) NOT NULL,
    nickname VARCHAR(255) NULL,
    nickname_search VARCHAR(255) NULL,
    generation INT NULL,
    alive BOOLEAN NOT NULL,
    gender VARCHAR(32) NULL,
    birth_year INT NULL,
    location_search VARCHAR(255) NULL,
    PRIMARY KEY (member_key),
    UNIQUE KEY uk_reporting_member_external_id (external_id),
    KEY ix_reporting_member_tree_name (tree_key, full_name_search),
    KEY ix_reporting_member_tree_nickname (tree_key, nickname_search)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
