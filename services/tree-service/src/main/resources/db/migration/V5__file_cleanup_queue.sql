CREATE TABLE file_cleanup_queue (
    cleanup_key BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tree_key BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (cleanup_key),
    KEY ix_file_cleanup_unprocessed (processed_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
