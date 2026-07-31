package com.familya.media.adapter.out.events;

import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.application.port.out.MediaOutbox;
import com.familya.media.domain.event.MediaChange;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter đầu ra (outbound) — chuyển {@link com.familya.media.domain.event.MediaChange}
 * sang bản tin outbox tương ứng.
 * <p>
 * Hỗ trợ tất cả subtype của {@code MediaChange}: Quarantined, Scanned,
 * Associated, Detached, AlbumCreated, Activated. Mỗi subtype có payload riêng
 * (phase/verdict/target/relationKind/name/activatedAt) được thêm vào payload
 * tổng quát (treeId, mediaId, eventType, eventVersion, revision, occurredAt).
 */
@Component
public class OutboxMediaChangePublisher implements MediaChangePublisher {

    private final MediaOutbox outbox;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo publisher.
     *
     * @param outbox  outbox để stage bản tin.
     * @param metrics metrics nền tảng.
     */
    public OutboxMediaChangePublisher(MediaOutbox outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    /**
     * Stage một domain event {@link MediaChange} ra outbox.
     * <p>
     * Payload chung gồm treeId, mediaId, eventType, eventVersion, revision,
     * occurredAt. Tùy subtype sẽ bổ sung các trường riêng.
     *
     * @param change domain event cần publish.
     */
    @Override
    public void publish(MediaChange change) {
        // Payload cơ sở — dùng LinkedHashMap để giữ thứ tự trường ổn định cho consumer test.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", change.treeId().toString());
        payload.put("mediaId", change.mediaId().toString());
        payload.put("eventType", change.eventType());
        payload.put("eventVersion", change.eventVersion());
        payload.put("revision", change.revision());
        payload.put("occurredAt", change.occurredAt().toString());
        // Pattern matching cho từng subtype — bổ sung trường payload riêng.
        if (change instanceof com.familya.media.domain.event.MediaQuarantined q) {
            payload.put("phase", q.phase());
            payload.put("reason", q.reason());
        } else if (change instanceof com.familya.media.domain.event.MediaScanned s) {
            payload.put("verdict", s.verdict());
        } else if (change instanceof com.familya.media.domain.event.MediaAssociated a) {
            payload.put("targetKind", a.targetKind());
            payload.put("targetId", a.targetId().toString());
        } else if (change instanceof com.familya.media.domain.event.MediaDetached d) {
            payload.put("relationKind", d.relationKind());
        } else if (change instanceof com.familya.media.domain.event.AlbumCreated ac) {
            payload.put("name", ac.name());
        } else if (change instanceof com.familya.media.domain.event.MediaActivated ma) {
            payload.put("activatedAt", ma.activatedAt().toString());
        }
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("media", change.mediaId().toString(), change.revision(),
                        change.eventType(), change.eventVersion(),
                        change.topic(), change.partitionKey(), payload);
        b.header("eventType", change.eventType());
        b.header("eventVersion", String.valueOf(change.eventVersion()));
        b.header("treeId", change.treeId().toString());
        b.header("mediaId", change.mediaId().toString());
        outbox.stage(b.build());
        metrics.outboxStaged("media-service", change.eventType());
    }
}
