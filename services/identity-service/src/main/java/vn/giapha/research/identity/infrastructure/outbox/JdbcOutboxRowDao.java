package vn.giapha.research.identity.infrastructure.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxRowDao implements OutboxRowDao {
    private final JdbcTemplate jdbc;

    public JdbcOutboxRowDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID append(UUID id, String source, String aggregateId, Long treeKey,
            String eventType, String payloadJson) {
        jdbc.update("INSERT INTO outbox(id, aggregate_type, aggregate_id, tree_key, "
                        + "event_type, payload_json, created_at, attempts) VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                id.toString(), source, aggregateId, treeKey, eventType, payloadJson,
                Timestamp.from(Instant.now()));
        return id;
    }

    @Override
    public List<Row> fetchUnpublished(int limit) {
        return jdbc.query("SELECT id, aggregate_type, aggregate_id, tree_key, event_type, "
                        + "payload_json, created_at, attempts FROM outbox WHERE published_at IS NULL "
                        + "ORDER BY created_at ASC LIMIT ?",
                (rs, index) -> new Row(UUID.fromString(rs.getString("id")),
                        rs.getString("aggregate_type"), rs.getString("aggregate_id"),
                        rs.getObject("tree_key") == null ? null : rs.getLong("tree_key"),
                        rs.getString("event_type"), rs.getString("payload_json"),
                        rs.getTimestamp("created_at").toInstant(), rs.getInt("attempts")), limit);
    }

    @Override
    public void markPublished(UUID id, Instant publishedAt) {
        jdbc.update("UPDATE outbox SET published_at = ? WHERE id = ?",
                Timestamp.from(publishedAt), id.toString());
    }

    @Override
    public void incrementAttempts(UUID id) {
        jdbc.update("UPDATE outbox SET attempts = attempts + 1 WHERE id = ?", id.toString());
    }
}
