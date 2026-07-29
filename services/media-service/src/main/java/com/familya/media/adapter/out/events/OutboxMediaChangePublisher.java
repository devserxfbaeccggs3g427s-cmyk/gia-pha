package com.familya.media.adapter.out.events;

import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.domain.event.MediaChange;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OutboxMediaChangePublisher implements MediaChangePublisher {

    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    public OutboxMediaChangePublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Override
    public void publish(MediaChange change) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", change.treeId().toString());
        payload.put("mediaId", change.mediaId().toString());
        payload.put("eventType", change.eventType());
        payload.put("eventVersion", change.eventVersion());
        payload.put("revision", change.revision());
        payload.put("occurredAt", change.occurredAt().toString());
        // attach kind if present (AlbumCreated/MediaQuarantined don't carry one)
        if (change instanceof com.familya.media.domain.event.MediaQuarantined) {
            payload.put("phase", "QUARANTINED");
        } else if (change instanceof com.familya.media.domain.event.MediaScanned s) {
            payload.put("verdict", s.verdict());
        } else if (change instanceof com.familya.media.domain.event.MediaAssociated a) {
            payload.put("targetKind", a.targetKind());
            payload.put("targetId", a.targetId().toString());
        } else if (change instanceof com.familya.media.domain.event.MediaDetached d) {
            payload.put("relationKind", d.relationKind());
        } else if (change instanceof com.familya.media.domain.event.AlbumCreated ac) {
            payload.put("name", ac.name());
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
