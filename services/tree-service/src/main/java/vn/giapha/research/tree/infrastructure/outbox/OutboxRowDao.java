package vn.giapha.research.tree.infrastructure.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRowDao {
    UUID append(UUID id, String source, String aggregateId, Long treeKey,
            String eventType, String payloadJson);
    List<Row> fetchUnpublished(int limit);
    void markPublished(UUID id, Instant publishedAt);
    void incrementAttempts(UUID id);

    record Row(UUID id, String payloadJson) {}
}
