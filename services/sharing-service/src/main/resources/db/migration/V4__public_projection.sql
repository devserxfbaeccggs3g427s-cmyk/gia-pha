CREATE TABLE public_members (
    tree_key BIGINT UNSIGNED NOT NULL,
    external_id VARCHAR(300) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    nickname VARCHAR(255) NULL,
    generation INT NULL,
    gender VARCHAR(32) NULL,
    alive BOOLEAN NOT NULL,
    birth_year INT NULL,
    PRIMARY KEY (tree_key, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE public_relationships (
    tree_key BIGINT UNSIGNED NOT NULL,
    external_id VARCHAR(300) NOT NULL,
    source_member_external_id VARCHAR(300) NOT NULL,
    target_member_external_id VARCHAR(300) NOT NULL,
    type VARCHAR(32) NOT NULL,
    PRIMARY KEY (tree_key, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE public_events (
    tree_key BIGINT UNSIGNED NOT NULL,
    external_id VARCHAR(300) NOT NULL,
    title VARCHAR(255) NOT NULL,
    event_date VARCHAR(32) NULL,
    type VARCHAR(32) NOT NULL,
    PRIMARY KEY (tree_key, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE public_media (
    tree_key BIGINT UNSIGNED NOT NULL,
    external_id VARCHAR(300) NOT NULL,
    PRIMARY KEY (tree_key, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
