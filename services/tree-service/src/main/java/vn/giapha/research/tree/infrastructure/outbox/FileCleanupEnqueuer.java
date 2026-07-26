package vn.giapha.research.tree.infrastructure.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FileCleanupEnqueuer {
    private final JdbcTemplate jdbc;

    public FileCleanupEnqueuer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueueForTreeDeletion(long treeKey) {
        jdbc.update("INSERT INTO file_cleanup_queue(tree_key, created_at) VALUES (?, UTC_TIMESTAMP(6))",
                treeKey);
    }
}
