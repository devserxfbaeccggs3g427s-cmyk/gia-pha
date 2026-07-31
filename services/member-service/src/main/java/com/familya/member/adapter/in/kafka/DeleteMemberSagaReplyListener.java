package com.familya.member.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.member.application.port.in.DeleteMemberSagaReplyCommand;
import com.familya.member.application.usecase.DeleteMemberSagaReplyProcessor;
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
 * Kafka listener cho các reply của Saga xóa thành viên. Lắng nghe bốn topic reply
 * từ các dịch vụ tham gia (relationship, event, media, tree-access) và chuyển tiếp
 * tới {@link DeleteMemberSagaReplyProcessor} để cập nhật trạng thái Saga.
 *
 * <p>Đây là một {@code @Component} thuộc tầng adapter-in trong kiến trúc Hexagonal, có
 * nhiệm vụ kết nối hạ tầng Kafka với tầng ứng dụng. Có các biện pháp bảo vệ:
 * <ul>
 *   <li>Inbox dedup: mỗi bản tin chỉ xử lý một lần thông qua {@link InboxStore}.</li>
 *   <li>Poison handling: bản tin JSON lỗi hoặc thiếu trường được chuyển sang dead-letter.</li>
 *   <li>Retry exhaustion: nếu processor ném lỗi, ghi dead-letter để điều tra.</li>
 * </ul>
 */
@Component
public class DeleteMemberSagaReplyListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaReplyListener.class);
    /** Mã định danh consumer — dùng cho inbox dedup và metric. */
    private static final String CONSUMER = "member-service.delete-member";

    private final DeleteMemberSagaReplyProcessor processor;
    private final InboxStore inbox;
    private final PlatformMetrics metrics;
    private final DeleteMemberSagaDeadLetterStore deadLetterStore;
    private final ObjectMapper json;

    /**
     * Khởi tạo listener với các phụ thuộc cần thiết.
     *
     * @param processor       bộ xử lý reply ở tầng use case
     * @param inbox           kho inbox để chống trùng lặp bản tin
     * @param metrics         bộ thu thập metric nền tảng
     * @param deadLetterStore kho dead-letter cho các bản tin lỗi
     * @param json            mapper JSON dùng để phân tích payload reply
     */
    public DeleteMemberSagaReplyListener(DeleteMemberSagaReplyProcessor processor,
                                          InboxStore inbox,
                                          PlatformMetrics metrics,
                                          DeleteMemberSagaDeadLetterStore deadLetterStore,
                                          ObjectMapper json) {
        this.processor = processor;
        this.inbox = inbox;
        this.metrics = metrics;
        this.deadLetterStore = deadLetterStore;
        this.json = json;
    }

    /**
     * Xử lý một bản tin reply nhận được từ Kafka. Phương thức này:
     * <ol>
     *   <li>Đánh dấu đã xử lý qua inbox (chống trùng).</li>
     *   <li>Phân tích JSON reply — nếu lỗi thì chuyển sang dead-letter.</li>
     *   <li>Ủy quyền xử lý cho {@link DeleteMemberSagaReplyProcessor}.</li>
     *   <li>Ghi dead-letter nếu processor ném ngoại lệ.</li>
     * </ol>
     *
     * @param record bản tin Kafka gốc từ một trong bốn topic reply
     */
    @KafkaListener(
            topics = {
                    "relationship.replies.v1",
                    "event.replies.v1",
                    "media.replies.v1",
                    "tree-access.replies.v1"
            },
            groupId = "${spring.application.name:member-service}.delete-member")
    @Transactional
    public void onReply(ConsumerRecord<String, Object> record) {
        // Ưu tiên dùng event_id từ header để làm khóa dedup; nếu thiếu thì tự tạo từ (topic, partition, offset)
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            eventId = "offset:" + record.topic() + ":" + record.partition() + ":" + record.offset();
        }
        // Bỏ qua nếu bản tin đã được xử lý trước đó (inbox đảm bảo idempotency)
        if (inbox.exists(eventId, CONSUMER)) {
            metrics.consumerDuplicate(CONSUMER, record.topic());
            return;
        }

        DeleteMemberSagaReplyCommand cmd;
        try {
            cmd = parseReply(record);
        } catch (RuntimeException parseError) {
            // Phân tích JSON thất bại: ghi dead-letter và đánh dấu đã xử lý để không lặp lại
            deadLetterStore.savePoison(record, parseError);
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }
        if (cmd == null) {
            // Payload rỗng hoặc không có nội dung: cũng coi là poison
            deadLetterStore.savePoison(record, new IllegalArgumentException("Empty delete-member reply"));
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }

        try {
            processor.process(cmd);
        } catch (RuntimeException processError) {
            // Use case từ chối reply (ví dụ: state không hợp lệ): ghi dead-letter với mã RETRY_EXHAUSTED
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
     * Phân tích bản tin Kafka reply thành {@link DeleteMemberSagaReplyCommand}.
     *
     * <p>Quy ước bước (step code) của các dịch vụ tham gia dùng tiền tố {@code RESTORE_*} cho
     * compensation, nhưng state machine của Saga xóa thành viên lại dùng {@code DISABLE_*}/
     * {@code DETACH_*}. Hàm {@link #forwardStepCode} chuyển các mã này về dạng thuận (forward)
     * để so khớp với step đã đăng ký.
     *
     * @param record bản tin Kafka gốc
     * @return lệnh reply đã phân tích, hoặc {@code null} nếu payload rỗng
     * @throws IllegalArgumentException nếu JSON thiếu trường bắt buộc hoặc không hợp lệ
     */
    private DeleteMemberSagaReplyCommand parseReply(ConsumerRecord<String, Object> record) {
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
            throw illegal;
        }
        String participant = requiredText(n, "participantService");
        String stepCode = requiredText(n, "stepCode");
        String status = requiredText(n, "status");
        boolean compensationReply = stepCode.startsWith("RESTORE_");
        boolean failed = "FAILED".equals(status);
        boolean compensationApplied = compensationReply || "COMPENSATED".equals(status);
        stepCode = forwardStepCode(stepCode);
        long appliedVersion = n.hasNonNull("appliedAggregateVersion")
                ? n.path("appliedAggregateVersion").asLong() : 0L;
        long appliedEpoch = n.hasNonNull("appliedEpoch")
                ? n.path("appliedEpoch").asLong() : 0L;
        String failureCode = n.hasNonNull("failureCode") ? n.path("failureCode").asText() : null;
        String failureMessage = n.hasNonNull("failureMessage") ? n.path("failureMessage").asText() : null;
        return new DeleteMemberSagaReplyCommand(operationId, participant, stepCode,
                appliedVersion, appliedEpoch, compensationApplied, failed,
                failureCode, failureMessage);
    }

    /**
     * Ánh xạ step code compensation từ dạng {@code RESTORE_*} của participant về dạng
     * thuận {@code DISABLE_*} / {@code DETACH_*} của Saga owner. Các mã không khớp được
     * giữ nguyên.
     *
     * @param stepCode mã bước compensation hoặc forward gốc
     * @return mã bước forward tương ứng
     */
    private static String forwardStepCode(String stepCode) {
        return switch (stepCode) {
            case "RESTORE_MEMBER_RELATIONSHIPS" -> "DISABLE_RELATIONSHIPS";
            case "RESTORE_MEMBER_EVENT_REFERENCES" -> "DETACH_EVENT_REFERENCES";
            case "RESTORE_MEMBER_MEDIA_REFERENCES" -> "DETACH_MEDIA_REFERENCES";
            default -> stepCode;
        };
    }

    /**
     * Đọc trường chuỗi bắt buộc từ JSON. Ném ngoại lệ nếu trường vắng mặt hoặc null.
     *
     * @param n     nút JSON gốc
     * @param field tên trường cần đọc
     * @return giá trị chuỗi của trường
     * @throws IllegalArgumentException nếu trường vắng mặt hoặc null
     */
    private static String requiredText(JsonNode n, String field) {
        JsonNode child = n.get(field);
        if (child == null || child.isNull()) {
            throw new IllegalArgumentException("Missing required field " + field);
        }
        return child.asText();
    }

    /**
     * Lấy giá trị header của bản tin Kafka theo tên. Trả về {@code null} nếu header không tồn tại.
     *
     * @param record bản tin Kafka
     * @param name   tên header cần đọc
     * @return giá trị header dạng chuỗi, hoặc {@code null}
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}