/**
 * Listener tiêu thụ các phản hồi Saga từ mọi participant service
 * trên topic chia sẻ {@code saga.replies.v1}.
 *
 * <p>Consumer này dành riêng cho orchestrator của audit-ops và sử dụng
 * {@code InboxStore} để chống trùng lặp. Các phản hồi cho operation
 * không xác định sẽ được bỏ qua (operation có thể thuộc orchestrator
 * khác hoặc đã bị xoá).</p>
 *
 * <p>Listener được khai báo bằng {@link KafkaListener} nên Spring Kafka
 * quản lý offset của consumer-group; việc commit thủ công và phân
 * vùng tuân theo cấu hình mặc định của platform starter
 * (Task 4 / ADR-004).</p>
 */
package com.familya.auditops.adapter.in.kafka;

import com.familya.auditops.application.port.in.RecordParticipantReplyCommand;
import com.familya.auditops.application.usecase.SagaOrchestrator;
import com.familya.platform.inbox.InboxRecord;
import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Component Spring xử lý phản hồi Saga nhận được từ Kafka.
 *
 * <p>Quy trình xử lý mỗi message:</p>
 * <ol>
 *   <li>Đọc header {@code event_id}; nếu thiếu thì bỏ qua và cảnh báo.</li>
 *   <li>Kiểm tra inbox để chống trùng lặp; nếu đã xử lý thì bỏ qua.</li>
 *   <li>Đánh dấu đã xử lý trong inbox trước khi parse để đảm bảo
 *       message lỗi cũng không bị xử lý lại nhiều lần.</li>
 *   <li>Parse JSON thành {@link RecordParticipantReplyCommand}.</li>
 *   <li>Gọi {@link SagaOrchestrator#applyReply} để cập nhật máy trạng thái.</li>
 * </ol>
 */
@Component
public class SagaReplyListener {

    /** Logger ghi lại các cảnh báo và lỗi khi xử lý phản hồi Saga. */
    private static final Logger LOG = LoggerFactory.getLogger(SagaReplyListener.class);
    /** Tên consumer cho inbox dedup và metric. */
    private static final String CONSUMER = "audit-ops-service";

    /** Orchestrator để cập nhật máy trạng thái Saga. */
    private final SagaOrchestrator orchestrator;
    /** Inbox chống trùng lặp của platform. */
    private final InboxStore inbox;
    /** Metric collector. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo listener.
     *
     * @param orchestrator orchestrator Saga
     * @param inbox        inbox dedup
     * @param metrics      metric collector
     */
    public SagaReplyListener(SagaOrchestrator orchestrator,
                             InboxStore inbox,
                             PlatformMetrics metrics) {
        this.orchestrator = orchestrator;
        this.inbox = inbox;
        this.metrics = metrics;
    }

    /**
     * Hàm xử lý một message Kafka từ {@code saga.replies.v1}.
     *
     * @param record bản ghi Kafka nhận được
     */
    @KafkaListener(topics = "saga.replies.v1", groupId = "${spring.application.name:audit-ops-service}")
    public void onReply(ConsumerRecord<String, Object> record) {
        // Bước 1: lấy event_id; nếu thiếu thì không có cách dedup nên bỏ qua.
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping reply without event_id topic={} offset={}", record.topic(), record.offset());
            return;
        }
        // Bước 2: chống trùng lặp thông qua inbox.
        if (inbox.exists(eventId, CONSUMER)) {
            metrics.consumerDuplicate(CONSUMER, record.topic());
            return;
        }
        // Bước 3: đánh dấu đã xử lý sớm để đảm bảo tính idempotent
        // ngay cả khi parse hoặc áp dụng thất bại.
        inbox.markProcessed(new InboxRecord(
                eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));

        try {
            // Bước 4: parse JSON sang command.
            RecordParticipantReplyCommand cmd = parseReply(record);
            if (cmd == null) {
                LOG.warn("Dropping malformed reply event_id={} offset={}", eventId, record.offset());
                return;
            }
            // Bước 5: chuyển command cho orchestrator xử lý.
            orchestrator.applyReply(cmd);
            metrics.consumerProcessed(CONSUMER, record.topic());
        } catch (RuntimeException e) {
            // Lỗi xử lý: log và ghi nhận metric failed. Không ném lại
            // để tránh retry vô tận; orchestrator sẽ retry ở lần sau
            // hoặc chuyển sang dead-letter ở cấp Saga.
            LOG.error("Saga reply processing failed event_id={} offset={}", eventId, record.offset(), e);
            metrics.consumerDuplicate(CONSUMER, "saga.reply.failed");
        }
    }

    /**
     * Parse phản hồi Saga từ record Kafka sang {@link RecordParticipantReplyCommand}.
     * Trả về {@code null} nếu payload không phải chuỗi hoặc thiếu trường bắt buộc.
     *
     * @param record bản ghi Kafka
     * @return command đã parse hoặc null nếu lỗi
     */
    private static RecordParticipantReplyCommand parseReply(ConsumerRecord<String, Object> record) {
        Object value = record.value();
        if (!(value instanceof String s) || s.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
            UUID operationId = UUID.fromString(requiredText(n, "operationId"));
            String participant = requiredText(n, "participantService");
            String step = requiredText(n, "stepName");
            String outcomeStr = requiredText(n, "outcome");
            RecordParticipantReplyCommand.Outcome outcome =
                    RecordParticipantReplyCommand.Outcome.valueOf(outcomeStr);
            Long ackedRevision = n.hasNonNull("ackedRevision") ? n.get("ackedRevision").asLong() : null;
            Long ackedEpoch = n.hasNonNull("ackedEpoch") ? n.get("ackedEpoch").asLong() : null;
            Long expectedVersion = n.hasNonNull("expectedVersion") ? n.get("expectedVersion").asLong() : null;
            String errorCode = n.hasNonNull("errorCode") ? n.get("errorCode").asText() : null;
            String errorMessage = n.hasNonNull("errorMessage") ? n.get("errorMessage").asText() : null;
            String correlationId = headerString(record, "correlation_id");
            String causationId = headerString(record, "causation_id");
            return new RecordParticipantReplyCommand(operationId, participant, step, outcome,
                    ackedRevision, ackedEpoch, expectedVersion,
                    errorCode, errorMessage, correlationId, causationId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Lấy giá trị chuỗi bắt buộc từ {@link com.fasterxml.jackson.databind.JsonNode}.
     *
     * @param n     node JSON
     * @param field tên trường cần đọc
     * @return chuỗi giá trị
     * @throws IllegalArgumentException nếu trường không tồn tại hoặc là null
     */
    private static String requiredText(com.fasterxml.jackson.databind.JsonNode n, String field) {
        com.fasterxml.jackson.databind.JsonNode child = n.get(field);
        if (child == null || child.isNull()) {
            throw new IllegalArgumentException("Missing required field " + field);
        }
        return child.asText();
    }

    /**
     * Lấy giá trị header của record Kafka theo tên.
     *
     * @param record bản ghi Kafka
     * @param name   tên header
     * @return giá trị chuỗi hoặc null nếu không tồn tại
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}