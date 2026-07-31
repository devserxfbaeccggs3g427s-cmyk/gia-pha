package com.familya.identity.adapter.out.events;

import com.familya.identity.application.port.out.IdentityEventPublisher;
import com.familya.identity.domain.event.IdentityEvent;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adapter xuất sự kiện domain ra bên ngoài thông qua Outbox Pattern.
 *
 * <p>Thay vì ghi trực tiếp vào Kafka, các sự kiện domain được "stage"
 * (đánh dấu sẵn sàng) vào bảng outbox trong cùng transaction với thao
 * tác ghi cơ sở dữ liệu. Một relay riêng (do {@code platform-outbox-starter}
 * cung cấp) sẽ đọc các bản ghi đã stage và phát hành chúng tới Kafka
 * theo cơ chế at-least-once với idempotency ở phía consumer.
 *
 * <p>Lợi ích:
 * <ul>
 *     <li>Đảm bảo tính nhất quán giữa dữ liệu và sự kiện (transaction
 *         cơ sở dữ liệu bao trùm cả hai).</li>
 *     <li>Consumer không cần thực hiện "dual-write" – tránh lỗi khi
 *         một trong hai thao tác thất bại.</li>
 *     <li>Cho phép replay sự kiện khi cần thiết.</li>
 * </ul>
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@Component
public class OutboxIdentityEventPublisher implements IdentityEventPublisher {

    /** Bộ ghi outbox chuẩn của nền tảng. */
    private final OutboxWriter outbox;
    /** Bộ thu thập metric. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo publisher với các phụ thuộc.
     *
     * @param outbox  bộ ghi outbox.
     * @param metrics bộ thu thập metric.
     */
    public OutboxIdentityEventPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    /**
     * Stage một sự kiện identity vào outbox.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Đóng gói các thông tin cốt lõi của sự kiện (userId, loại,
     *         phiên bản, thời điểm xảy ra) thành một {@link Map} để
     *         làm payload JSON.</li>
     *     <li>Tạo {@link com.familya.platform.outbox.JdbcOutboxWriter.Builder}
     *         với aggregate type {@code "user"}, ID là {@code userId}
     *         và topic {@code identity.events.v1} – đây là topic chuẩn
     *         cho tất cả sự kiện phát ra từ identity-service.</li>
     *     <li>Thêm header {@code eventType} và {@code eventVersion}
     *         để consumer có thể định tuyến / chuyển đổi phiên bản
     *         schema.</li>
     *     <li>Gọi {@link OutboxWriter#stage} để đưa bản ghi vào
     *         outbox.</li>
     *     <li>Tăng metric {@code outbox.staged} để giám sát lưu lượng
     *         sự kiện.</li>
     * </ol>
     *
     * @param event sự kiện domain cần phát hành.
     */
    @Override
    public void publish(IdentityEvent event) {
        // Bước 1: đóng gói payload dạng Map để dễ dàng serialize sang JSON.
        Map<String, Object> payload = Map.of(
                "userId", event.userId().toString(),
                "eventType", event.eventType(),
                "eventVersion", event.eventVersion(),
                "occurredAt", event.occurredAt().toString()
        );
        // Bước 2: khởi tạo builder với aggregate type "user" và topic chuẩn.
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("user", event.userId().toString(), 1L,
                        event.eventType(), event.eventVersion(),
                        "identity.events.v1", event.userId().toString(), payload);
        // Bước 3: thêm header phục vụ định tuyến / versioning phía consumer.
        b.header("eventType", event.eventType());
        b.header("eventVersion", String.valueOf(event.eventVersion()));
        // Bước 4: đưa bản ghi vào outbox – relay sẽ xử lý phát hành tới Kafka.
        outbox.stage(b.build());
        // Bước 5: tăng metric để giám sát lưu lượng sự kiện.
        metrics.outboxStaged("identity-service", event.eventType());
    }
}
