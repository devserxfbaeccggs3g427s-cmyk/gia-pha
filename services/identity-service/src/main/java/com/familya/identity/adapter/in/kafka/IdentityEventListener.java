package com.familya.identity.adapter.in.kafka;

import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Adapter Kafka đầu vào chịu trách nhiệm lắng nghe các sự kiện liên
 * quan đến {@code tree.memberships.v1} và {@code member.events.v1}.
 *
 * <p>Mục tiêu chính:
 * <ul>
 *     <li>Đảm bảo idempotency thông qua cơ chế {@link InboxStore} –
 *         mỗi sự kiện chỉ được xử lý đúng một lần, tránh hiệu ứng phụ
 *         khi consumer khởi động lại hoặc nhận lại message.</li>
 *     <li>Phát sinh metric giám sát thông qua {@link PlatformMetrics}.</li>
 *     <li>Cung cấp điểm mở rộng để thêm logic nghiệp vụ khi cần phản ứng
 *         với thay đổi về thành viên cây gia phả.</li>
 * </ul>
 *
 * <p>Việc sử dụng {@link InboxStore} giúp giải quyết vấn đề
 * "exactly-once" semantics trong các hệ thống phân tán: thay vì dựa
 * vào tính idempotent của mọi consumer, chúng ta lưu lại "dấu vết"
 * đã xử lý trong cơ sở dữ liệu quan hệ.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@Component
public class IdentityEventListener {

    /** Logger ghi lại hoạt động của listener. */
    private static final Logger LOG = LoggerFactory.getLogger(IdentityEventListener.class);

    /** Cổng ra (port) truy cập dữ liệu identity. Hiện chưa sử dụng trực tiếp trong listener này nhưng được giữ để sẵn sàng mở rộng. */
    private final IdentityRepository repo;
    /** Kho inbox dùng để chống trùng lặp sự kiện. */
    private final InboxStore inbox;
    /** Bộ thu thập metric cho dịch vụ. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo listener với các phụ thuộc bắt buộc.
     *
     * @param repo    {@link IdentityRepository} dùng để truy vấn / cập nhật người dùng.
     * @param inbox   {@link InboxStore} dùng để ghi nhận event đã xử lý.
     * @param metrics {@link PlatformMetrics} dùng để phát sinh metric.
     */
    public IdentityEventListener(IdentityRepository repo, InboxStore inbox, PlatformMetrics metrics) {
        this.repo = repo;
        this.inbox = inbox;
        this.metrics = metrics;
    }

    /**
     * Lắng nghe topic {@code tree.memberships.v1}.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Gọi {@link #shouldProcess} để kiểm tra idempotency; nếu
     *         sự kiện đã được xử lý hoặc thiếu header {@code event_id}
     *         thì bỏ qua.</li>
     *     <li>Ghi log với khóa và offset để phục vụ truy vết.</li>
     *     <li>Tăng metric {@code identity-service.consumer.processed}
     *         với nhãn {@code membership} để giám sát.</li>
     * </ol>
     *
     * @param record bản ghi Kafka chứa khóa, giá trị và headers.
     */
    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecord<String, Object> record) {
        // Bước 1: kiểm tra idempotency; nếu đã xử lý hoặc thiếu event_id -> bỏ qua.
        if (!shouldProcess(record, "tree.memberships.v1")) return;
        // Bước 2: log khóa + offset để dễ truy vết khi cần điều tra sự cố.
        LOG.info("Received membership event key={} offset={}", record.key(), record.offset());
        // Bước 3: cập nhật metric phục vụ giám sát vận hành.
        metrics.consumerProcessed("identity-service", "membership");
    }

    /**
     * Lắng nghe topic {@code member.events.v1}.
     *
     * <p>Quy trình xử lý tương tự {@link #onMembership} nhưng áp dụng
     * cho sự kiện thành viên (member). Hiện tại chỉ ghi log và tăng
     * metric – đây là điểm mở rộng tự nhiên nếu sau này cần đồng bộ
     * thông tin người dùng với identity.
     *
     * @param record bản ghi Kafka chứa khóa, giá trị và headers.
     */
    @KafkaListener(topics = "member.events.v1", groupId = "${spring.application.name}")
    public void onMember(ConsumerRecord<String, Object> record) {
        // Bước 1: kiểm tra idempotency giống như xử lý membership.
        if (!shouldProcess(record, "member.events.v1")) return;
        // Bước 2: log khóa + offset.
        LOG.info("Received member event key={} offset={}", record.key(), record.offset());
        // Bước 3: tăng metric với nhãn "member" để phân biệt với membership.
        metrics.consumerProcessed("identity-service", "member");
    }

    /**
     * Kiểm tra và ghi nhận một sự kiện đã được xử lý hay chưa.
     *
     * <p>Quy trình:
     * <ol>
     *     <li>Đọc header {@code event_id} – nếu thiếu thì bỏ qua và
     *         cảnh báo, bởi vì sự kiện không có id đồng nghĩa với
     *         không thể đảm bảo idempotency.</li>
     *     <li>Tra cứu trong {@link InboxStore}. Nếu đã tồn tại thì
     *         đánh dấu trùng lặp (duplicate) qua metric và trả về
     *         {@code false} để bỏ qua.</li>
     *     <li>Nếu chưa tồn tại, ghi nhận {@link com.familya.platform.inbox.InboxRecord}
     *         với thời điểm hiện tại và trả về {@code true} để tiếp tục
     *         xử lý.</li>
     * </ol>
     *
     * @param record bản ghi Kafka.
     * @param topic  tên topic (chỉ dùng cho log/metric).
     * @return {@code true} nếu sự kiện nên được xử lý, {@code false} nếu
     *         nên bỏ qua.
     */
    private boolean shouldProcess(ConsumerRecord<String, Object> record, String topic) {
        // Bước 1: lấy event_id từ header; thiếu thì cảnh báo và bỏ qua.
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        // Bước 2: kiểm tra inbox; nếu đã xử lý -> báo trùng và bỏ qua.
        if (inbox.exists(eventId, "identity-service")) {
            metrics.consumerDuplicate("identity-service", topic);
            return false;
        }
        // Bước 3: ghi nhận sự kiện đã được xử lý để những lần nhận lại
        // (replay) sẽ bị bỏ qua một cách idempotent.
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "identity-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    /**
     * Trích xuất giá trị header dạng chuỗi từ bản ghi Kafka.
     *
     * @param record bản ghi Kafka.
     * @param name   tên header cần đọc.
     * @return giá trị header (chuyển sang {@code String}), hoặc
     *         {@code null} nếu header không tồn tại.
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        // Dùng lastHeader để lấy giá trị mới nhất (một số producer có
        // thể ghi đè header nhiều lần).
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}
