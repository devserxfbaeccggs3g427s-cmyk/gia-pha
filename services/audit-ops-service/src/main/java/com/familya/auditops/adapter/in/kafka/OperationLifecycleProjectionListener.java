/**
 * Listener tiêu thụ các sự kiện vòng đời operation từ topic
 * {@code operations.events.v1} do các service sở hữu Saga phát ra
 * (Member, Tree Access, ...).
 *
 * <p>Audit Ops <b>không phải</b> participant nghiệp vụ; nó chỉ sao
 * chép (mirror) các sự kiện vòng đời để phục vụ giao diện operator
 * UI và khả năng replay lịch sử. Do đó, việc xử lý lỗi ở đây
 * không được làm ảnh hưởng tới tiến trình Saga của bounded context khác.</p>
 */
package com.familya.auditops.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.auditops.adapter.out.persistence.OperationLifecycleDeadLetterStore;
import com.familya.auditops.application.port.out.OperationLifecycleProjection;
import com.familya.auditops.domain.model.OperationLifecycleRow;
import com.familya.platform.inbox.InboxRecord;
import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Component Spring lắng nghe {@code operations.events.v1} và cập nhật
 * projection vòng đời operation ở database cục bộ.
 *
 * <p>Quy trình xử lý mỗi message gồm các bước:</p>
 * <ol>
 *   <li>Đọc header {@code event_id}; nếu thiếu thì đưa vào DLQ.</li>
 *   <li>Kiểm tra trong {@link InboxStore} để chống trùng lặp (idempotent).</li>
 *   <li>Parse payload JSON; nếu lỗi thì ghi DLQ rồi đánh dấu đã xử lý.</li>
 *   <li>Ánh xạ các trường sang {@link OperationLifecycleRow} và gọi
 *       {@link OperationLifecycleProjection} tương ứng.</li>
 *   <li>Đánh dấu đã xử lý trong inbox và ghi nhận metric.</li>
 * </ol>
 */
@Component
public class OperationLifecycleProjectionListener {

    /** Logger ghi lại lỗi parse và xử lý event. */
    private static final Logger LOG = LoggerFactory.getLogger(OperationLifecycleProjectionListener.class);
    /** Tên consumer dùng cho inbox dedup và metric. */
    private static final String CONSUMER = "audit-ops-service.lifecycle";

    /** Port projection để ghi trạng thái vòng đời vào database. */
    private final OperationLifecycleProjection projection;
    /** Inbox chống trùng lặp của platform. */
    private final InboxStore inbox;
    /** Metric collector của platform. */
    private final PlatformMetrics metrics;
    /** Kho DLQ cho các message lỗi. */
    private final OperationLifecycleDeadLetterStore deadLetterStore;

    /**
     * Khởi tạo listener với các phụ thuộc bắt buộc.
     *
     * @param projection    port ghi projection
     * @param inbox         inbox dedup
     * @param metrics       metric collector
     * @param deadLetterStore kho DLQ
     */
    public OperationLifecycleProjectionListener(OperationLifecycleProjection projection,
                                                InboxStore inbox,
                                                PlatformMetrics metrics,
                                                OperationLifecycleDeadLetterStore deadLetterStore) {
        this.projection = projection;
        this.inbox = inbox;
        this.metrics = metrics;
        this.deadLetterStore = deadLetterStore;
    }

    /**
     * Hàm xử lý chính, được gọi tự động bởi Spring Kafka cho mỗi record
     * từ {@code operations.events.v1}. Chạy trong transaction để đảm
     * bảo projection và inbox được commit cùng nhau.
     *
     * @param record bản ghi Kafka nhận được
     */
    @KafkaListener(topics = "operations.events.v1", groupId = "${spring.application.name:audit-ops-service}.lifecycle")
    @Transactional
    public void onEvent(ConsumerRecord<String, Object> record) {
        // Bước 1: lấy event_id từ header. Thiếu event_id thì không có cách
        // chống trùng lặp, do đó chuyển thẳng vào DLQ rồi bỏ qua.
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            deadLetterStore.save(record, new IllegalArgumentException("Missing event_id header"));
            return;
        }
        // Bước 2: kiểm tra inbox để chống trùng lặp.
        if (inbox.exists(eventId, CONSUMER)) {
            metrics.consumerDuplicate(CONSUMER, record.topic());
            return;
        }

        // Bước 3: parse JSON payload. Lỗi parse được ghi vào DLQ và
        // đánh dấu đã xử lý để tránh retry vô tận.
        JsonNode n;
        try {
            n = parse(record);
        } catch (RuntimeException parseError) {
            LOG.error("Malformed lifecycle event event_id={} offset={}", eventId, record.offset(), parseError);
            deadLetterStore.save(record, parseError);
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }
        if (n == null) {
            IllegalArgumentException error = new IllegalArgumentException("Empty lifecycle event payload");
            deadLetterStore.save(record, error);
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }

        try {
            // Bước 4: đọc các trường cần thiết, ưu tiên header rồi mới payload.
            String eventType = headerString(record, "event_type");
            if (eventType == null) eventType = n.path("eventType").asText("");

            UUID operationId = UUID.fromString(n.path("operationId").asText());
            UUID treeId = n.hasNonNull("treeId") ? UUID.fromString(n.path("treeId").asText()) : null;
            UUID userId = n.hasNonNull("initiatingUserId") ? UUID.fromString(n.path("initiatingUserId").asText()) : null;
            String ownerService = n.path("ownerService").asText("unknown");
            String sagaType = n.path("sagaType").asText("");
            Long targetVersion = n.hasNonNull("targetAggregateVersion") ? n.path("targetAggregateVersion").asLong() : null;
            Long targetEpoch = n.hasNonNull("targetEpoch") ? n.path("targetEpoch").asLong() : null;
            String state = n.path("state").asText("");
            String failureCode = n.hasNonNull("failureCode") ? n.path("failureCode").asText() : null;
            String failureMessage = n.hasNonNull("failureMessage") ? n.path("failureMessage").asText() : null;
            String failureRoutingField = n.hasNonNull("failureRouting") ? n.path("failureRouting").asText() : null;
            Instant startedAt = n.hasNonNull("startedAt")
                    ? Instant.parse(n.path("startedAt").asText())
                    : Instant.now();
            Instant occurredAt = n.hasNonNull("occurredAt")
                    ? Instant.parse(n.path("occurredAt").asText())
                    : Instant.now();

            // Quyết định failureRouting: ưu tiên trường trong payload,
            // nếu không có thì dựa vào state hiện tại.
            String routing = failureRoutingField != null ? failureRoutingField : failureRouting(state);
            OperationLifecycleRow row = new OperationLifecycleRow(
                    operationId, ownerService, sagaType, treeId, userId,
                    state, targetVersion, targetEpoch,
                    failureCode, failureMessage, routing, startedAt, occurredAt,
                    isTerminal(state) ? occurredAt : null);

            // Phân biệt sự kiện bắt đầu (OperationStarted) với sự kiện
            // chuyển trạng thái để chọn phương thức projection phù hợp.
            if ("OperationStarted".equals(eventType) || n.has("startedAt")) {
                projection.upsertStarted(row, eventId);
            } else {
                projection.applyStateChange(row, isTerminal(state) ? occurredAt : null, eventId);
            }
            // Bước 5: đánh dấu đã xử lý và ghi nhận metric.
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            metrics.consumerProcessed(CONSUMER, record.topic());
        } catch (RuntimeException e) {
            // Ném lại để transaction rollback và Kafka retry offset.
            LOG.error("Operation lifecycle projection failed event_id={} offset={}",
                    eventId, record.offset(), e);
            throw e;
        }
    }

    /**
     * Suy ra failureRouting từ trạng thái vòng đời của operation.
     *
     * @param state trạng thái hiện tại (SUCCEEDED, FAILED, ...)
     * @param chuỗi định tuyến tương ứng hoặc null nếu không áp dụng
     */
    private static String failureRouting(String state) {
        return switch (state) {
            case "COMPENSATING" -> "COMPENSATING";
            case "COMPENSATED" -> "COMPENSATED";
            case "MANUAL_REVIEW" -> "MANUAL_REVIEW";
            case "FAILED" -> "FAILED";
            case "DLQ", "DEAD_LETTERED" -> "DLQ";
            default -> null;
        };
    }

    /**
     * Kiểm tra trạng thái có phải trạng thái kết thúc (terminal) hay không.
     *
     * @param state trạng thái cần kiểm tra
     * @return true nếu là trạng thái kết thúc
     */
    private static boolean isTerminal(String state) {
        return "SUCCEEDED".equals(state) || "FAILED".equals(state)
                || "MANUAL_REVIEW".equals(state) || "CANCELLED".equals(state)
                || "COMPENSATED".equals(state);
    }

    /**
     * Parse payload của record Kafka sang {@link JsonNode}.
     * <p>
     * Hỗ trợ 3 dạng giá trị:
     * </p>
     * <ul>
     *   <li>{@link String}: parse trực tiếp.</li>
     *   <li>{@code byte[]}: chuyển sang chuỗi UTF-8 trước khi parse.</li>
     *   <li>Các kiểu khác: chuyển sang chuỗi bằng {@code toString()} rồi parse.</li>
     * </ul>
     *
     * @param record bản ghi Kafka
     * @return {@link JsonNode} đã được parse
     * @throws IllegalArgumentException nếu payload null, rỗng hoặc không phải JSON
     */
    private static JsonNode parse(ConsumerRecord<String, Object> record) {
        Object v = record.value();
        if (v == null) {
            throw new IllegalArgumentException("Null payload");
        }
        String s;
        if (v instanceof String str) {
            s = str;
        } else if (v instanceof byte[] bytes) {
            s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            s = v.toString();
        }
        if (s.isBlank()) {
            throw new IllegalArgumentException("Blank payload");
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JSON payload", e);
        }
    }

    /**
     * Lấy giá trị header của record Kafka theo tên (lấy header cuối cùng).
     *
     * @param record bản ghi Kafka
     * @param name   tên header cần đọc
     * @return giá trị chuỗi hoặc null nếu không tồn tại
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}