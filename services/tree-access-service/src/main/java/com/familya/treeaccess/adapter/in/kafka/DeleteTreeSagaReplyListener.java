package com.familya.treeaccess.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.platform.inbox.InboxRecord;
import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import com.familya.treeaccess.application.port.in.DeleteTreeSagaReplyCommand;
import com.familya.treeaccess.application.usecase.DeleteTreeSagaReplyProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka listener lắng nghe các reply của Saga delete-tree từ các bounded-context
 * tham gia (member, relationship, event, media, sharing, search). Mỗi reply có
 * thể là ACK (đã xử lý), FAILED (lỗi) hoặc COMPENSATED (đã rollback). Listener:
 *
 * <ul>
 *   <li>Đảm bảo idempotency thông qua {@link InboxStore} (dựa trên header {@code event_id}).</li>
 *   <li>Parse phản hồi thành {@link DeleteTreeSagaReplyCommand}.</li>
 *   <li>Đẩy poison message (không parse được) sang {@link DeleteTreeSagaDeadLetterStore}.</li>
 *   <li>Ủy quyền xử lý cho {@link DeleteTreeSagaReplyProcessor}.</li>
 * </ul>
 */
@Component
public class DeleteTreeSagaReplyListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaReplyListener.class);

    /** Tên consumer dùng cho metric và inbox; trùng với {@code consumer.id}. */
    private static final String CONSUMER = "tree-access-service.delete-tree";

    /** Bộ xử lý reply Saga ở tầng use-case. */
    private final DeleteTreeSagaReplyProcessor processor;

    /** Kho inbox dùng để chống trùng lặp giữa các lần consumer chạy lại. */
    private final InboxStore inbox;

    /** Bộ thu thập số liệu — đếm bản tin đã xử lý hoặc phát hiện trùng. */
    private final PlatformMetrics metrics;

    /** Kho dead-letter — nơi gửi các poison message. */
    private final DeleteTreeSagaDeadLetterStore deadLetterStore;

    /** ObjectMapper để parse payload JSON. */
    private final ObjectMapper json;

    /**
     * Khởi tạo listener với các phụ thuộc cần thiết.
     *
     * @param processor      bộ xử lý reply
     * @param inbox          cửa hàng inbox chống trùng lặp
     * @param metrics        metric giám sát
     * @param deadLetterStore DLQ
     * @param json           mapper JSON
     */
    public DeleteTreeSagaReplyListener(DeleteTreeSagaReplyProcessor processor,
                                       InboxStore inbox,
                                       PlatformMetrics metrics,
                                       DeleteTreeSagaDeadLetterStore deadLetterStore,
                                       ObjectMapper json) {
        this.processor = processor;
        this.inbox = inbox;
        this.metrics = metrics;
        this.deadLetterStore = deadLetterStore;
        this.json = json;
    }

    /**
     * Xử lý mỗi bản ghi reply đến từ Kafka. Quy trình:
     *
     * <ol>
     *   <li>Lấy {@code event_id} từ header — nếu thiếu thì tạo khoá từ topic:partition:offset.</li>
     *   <li>Nếu inbox đã thấy event_id này → bỏ qua (idempotency) và đếm trùng.</li>
     *   <li>Parse nội dung JSON thành {@link DeleteTreeSagaReplyCommand}. Khi parse lỗi → đẩy vào
     *       {@link DeleteTreeSagaDeadLetterStore} rồi đánh dấu xử lý xong.</li>
     *   <li>Ủy quyền cho {@link DeleteTreeSagaReplyProcessor}; nếu thất bại → ghi DLQ loại
     *       {@code RETRY_EXHAUSTED} và đánh dấu đã xử lý để tránh lặp vô hạn.</li>
     *   <li>Nếu thành công → đánh dấu inbox và tăng metric {@code consumerProcessed}.</li>
     * </ol>
     *
     * @param record bản ghi Kafka gốc
     */
    @KafkaListener(
            topics = {
                    "member.replies.v1",
                    "relationship.replies.v1",
                    "event.replies.v1",
                    "media.replies.v1",
                    "sharing.replies.v1",
                    "search.replies.v1"
            },
            groupId = "${spring.application.name:tree-access-service}.delete-tree")
    @Transactional
    public void onReply(ConsumerRecord<String, Object> record) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            // Một số producer cũ không gắn header event_id — fallback bằng "offset:…".
            eventId = "offset:" + record.topic() + ":" + record.partition() + ":" + record.offset();
        }
        if (inbox.exists(eventId, CONSUMER)) {
            // Idempotency: tránh xử lý lặp khi Kafka phân phối lại cùng một bản tin.
            metrics.consumerDuplicate(CONSUMER, record.topic());
            return;
        }

        DeleteTreeSagaReplyCommand cmd;
        try {
            cmd = parseReply(record);
        } catch (RuntimeException parseError) {
            // Không parse được → DLQ và đánh dấu đã xử lý để không xử lý lặp lại.
            deadLetterStore.savePoison(record, parseError);
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }
        if (cmd == null) {
            // Payload rỗng hoặc không phải chuỗi JSON → vẫn là poison, lưu DLQ.
            deadLetterStore.savePoison(record, new IllegalArgumentException("Empty delete-tree reply"));
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }

        try {
            processor.process(cmd);
        } catch (RuntimeException processError) {
            // Xử lý thất bại — ghi log, đẩy sang DLQ và đánh dấu đã xử lý.
            LOG.error("Saga processor rejected reply op={} step={} reason={}",
                    cmd.operationId(), cmd.stepCode(), processError.toString());
            deadLetterStore.saveRetryExhausted(
                    cmd.operationId(), cmd.participantService(), cmd.stepCode(),
                    -1, processError);
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }
        inbox.markProcessed(new InboxRecord(
                eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
        metrics.consumerProcessed(CONSUMER, record.topic());
    }

    /**
     * Chuyển phản hồi thô từ Kafka thành {@link DeleteTreeSagaReplyCommand}. Trong
     * quá trình chuyển đổi:
     *
     * <ul>
     *   <li>Mã bước dạng {@code RESTORE_*} được map sang mã forward tương ứng vì luồng
     *       công việc bên trong dùng cùng bước PURGE/REVOKE/TOMBSTONE.</li>
     *   <li>Cờ {@code compensationApplied} = (là RESTORE_*) hoặc status COMPENSATED.</li>
     *   <li>Các trường số thiếu sẽ mặc định 0; trường chuỗi lỗi có thể null.</li>
     * </ul>
     *
     * @param record bản ghi Kafka
     * @return lệnh Saga đã được chuẩn hoá hoặc {@code null} nếu payload rỗng
     * @throws IllegalArgumentException khi payload thiếu trường bắt buộc hoặc JSON lỗi
     */
    private DeleteTreeSagaReplyCommand parseReply(ConsumerRecord<String, Object> record) {
        Object v = record.value();
        if (!(v instanceof String s) || s.isBlank()) return null;
        JsonNode n;
        try {
            n = json.readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JSON", e);
        }
        UUID operationId;
        try {
            operationId = UUID.fromString(requiredText(n, "operationId"));
        } catch (IllegalArgumentException illegal) {
            // Truyền nguyên nhân gốc để người vận hành thấy UUID không hợp lệ.
            throw illegal;
        }
        String participant = requiredText(n, "participantService");
        String stepCode = requiredText(n, "stepCode");
        String status = requiredText(n, "status");
        // Reply bù (RESTORE_*) là dấu hiệu compensation.
        boolean compensationReply = stepCode.startsWith("RESTORE_");
        boolean failed = "FAILED".equals(status);
        boolean compensationApplied = compensationReply || "COMPENSATED".equals(status);
        // Chuẩn hoá mã bước về dạng "forward" để luồng xử lý nội bộ làm việc thống nhất.
        stepCode = forwardStepCode(stepCode);
        long appliedVersion = n.hasNonNull("appliedAggregateVersion")
                ? n.path("appliedAggregateVersion").asLong() : 0L;
        long appliedEpoch = n.hasNonNull("appliedEpoch")
                ? n.path("appliedEpoch").asLong() : 0L;
        String failureCode = n.hasNonNull("failureCode") ? n.path("failureCode").asText() : null;
        String failureMessage = n.hasNonNull("failureMessage") ? n.path("failureMessage").asText() : null;
        return new DeleteTreeSagaReplyCommand(operationId, participant, stepCode,
                appliedVersion, appliedEpoch, compensationApplied, failed,
                failureCode, failureMessage);
    }

    /**
     * Map mã bước dạng {@code RESTORE_*} về mã forward tương ứng. Vì các reply
     * compensation từ tham gia viên dùng tiền tố {@code RESTORE_}, cần quy đổi
     * về bước chính ({@code PURGE_*}…) để khớp với mô hình nội bộ. Với mã
     * không thuộc RESTORE thì giữ nguyên.
     *
     * @param stepCode mã bước nhận được từ reply
     * @return mã bước đã được chuẩn hoá
     */
    private static String forwardStepCode(String stepCode) {
        return switch (stepCode) {
            case "RESTORE_MEMBER_TREE" -> "PURGE_MEMBER_TREE";
            case "RESTORE_RELATIONSHIP_TREE" -> "PURGE_RELATIONSHIP_TREE";
            case "RESTORE_EVENT_TREE" -> "PURGE_EVENT_TREE";
            case "RESTORE_MEDIA_METADATA_TREE" -> "PURGE_MEDIA_METADATA_TREE";
            case "RESTORE_SHARING_TREE" -> "REVOKE_SHARING_TREE";
            case "RESTORE_SEARCH_TREE" -> "PURGE_SEARCH_TREE";
            case "RESTORE_TREE" -> "TOMBSTONE_TREE";
            default -> stepCode;
        };
    }

    /**
     * Lấy trường chuỗi bắt buộc từ cây JSON.
     *
     * @param n     đối tượng JSON gốc
     * @param field tên trường cần đọc
     * @return giá trị chuỗi nếu có
     * @throws IllegalArgumentException khi trường bị thiếu hoặc rỗng
     */
    private static String requiredText(JsonNode n, String field) {
        JsonNode child = n.get(field);
        if (child == null || child.isNull()) {
            throw new IllegalArgumentException("Missing required field " + field);
        }
        return child.asText();
    }

    /**
     * Trích xuất giá trị header {@code name} từ Kafka record.
     *
     * @param record bản ghi Kafka
     * @param name   tên header cần đọc
     * @return giá trị header dạng chuỗi hoặc {@code null} nếu header không tồn tại
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}