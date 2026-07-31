package com.familya.treeaccess.adapter.in.kafka;

import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import com.familya.treeaccess.application.port.out.TreeRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Membership consumer — re-builds the projection row from the
 * authoritative event stream. The local projection in Tree Access is
 * written in the same transaction as the event publication, so this
 * consumer is the replay/reconciliation path (used by Kafka replay,
 * projection rebuild, and cutover).
 */
@Component
public class MembershipProjectionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MembershipProjectionConsumer.class);

    /** Kho lưu trữ để cập nhật projection từ event stream. */
    private final TreeRepository repo;

    /** Kho inbox giúp chống xử lý trùng event giữa các lần rebalance. */
    private final InboxStore inbox;

    /** Bộ thu thập số liệu. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo consumer.
     *
     * @param repo    kho lưu trữ projection để cập nhật từ event stream
     * @param inbox   kho inbox chống trùng lặp
     * @param metrics metric giám sát
     */
    public MembershipProjectionConsumer(TreeRepository repo, InboxStore inbox, PlatformMetrics metrics) {
        this.repo = repo;
        this.inbox = inbox;
        this.metrics = metrics;
    }

    /**
     * Xử lý sự kiện thành viên từ topic {@code tree.memberships.v1}.
     *
     * <p>Trong chế độ hoạt động bình thường, projection đã được ghi đồng thời với
     * outbox tại {@link com.familya.treeaccess.adapter.out.events.OutboxTreeEventPublisher}
     * nên consumer này chỉ phục vụ replay/cutover. Khi được gọi, nó vẫn đảm bảo
     * idempotency thông qua inbox và phát metric.</p>
     *
     * @param record bản ghi Kafka chứa sự kiện membership
     */
    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecord<String, Object> record) {
        if (!shouldProcess(record, "tree.memberships.v1")) return;
        // Nhà phát hành nội bộ ghi projection cùng transaction với outbox,
        // nên consumer này chủ yếu phục vụ replay/cutover. Việc tái tạo projection
        // thực sự được thực hiện ở ReconciliationUseCase.
        metrics.consumerProcessed("tree-access-service", "membership");
        LOG.info("Processed membership event offset={} key={}", record.offset(), record.key());
    }

    /**
     * Xử lý sự kiện cây từ topic {@code tree.events.v1}. Tương tự
     * {@link #onMembership(ConsumerRecord)}, consumer này chỉ phục vụ replay/cutover.
     *
     * @param record bản ghi Kafka chứa sự kiện cây
     */
    @KafkaListener(topics = "tree.events.v1", groupId = "${spring.application.name}")
    public void onTree(ConsumerRecord<String, Object> record) {
        if (!shouldProcess(record, "tree.events.v1")) return;
        metrics.consumerProcessed("tree-access-service", "tree");
        LOG.info("Processed tree event offset={} key={}", record.offset(), record.key());
    }

    /**
     * Kiểm tra idempotency trước khi xử lý bản ghi Kafka. Trả về {@code true}
     * khi bản ghi mới và cần xử lý tiếp; {@code false} nếu đã xử lý hoặc thiếu
     * {@code event_id}.
     *
     * @param record bản ghi Kafka
     * @param topic  tên topic đang xử lý, dùng cho log và metric
     * @return {@code true} nếu bản ghi nên được xử lý tiếp
     */
    private boolean shouldProcess(ConsumerRecord<String, Object> record, String topic) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            // Không có event_id thì không đảm bảo idempotency — bỏ qua và ghi cảnh báo.
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        if (inbox.exists(eventId, "tree-access-service")) {
            // Đã xử lý trước đó — đếm trùng để giám sát.
            metrics.consumerDuplicate("tree-access-service", topic);
            return false;
        }
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "tree-access-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    /**
     * Đọc giá trị header từ Kafka record.
     *
     * @param record bản ghi Kafka
     * @param name   tên header
     * @return giá trị chuỗi hoặc {@code null}
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}