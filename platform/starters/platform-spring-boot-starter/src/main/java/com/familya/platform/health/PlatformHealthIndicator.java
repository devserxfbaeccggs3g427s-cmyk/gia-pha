package com.familya.platform.health;

import com.familya.platform.outbox.OutboxWriter;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Health indicator tuỳ chỉnh phục vụ Kubernetes readiness probe.
 *
 * <p>Indicator này kiểm tra hai yếu tố quan trọng để đánh giá một dịch vụ có
 * sẵn sàng nhận traffic hay không:</p>
 * <ul>
 *   <li><b>Khả năng kết nối cơ sở dữ liệu:</b> thực hiện {@code isValid(2)} trên
 *       một connection mới từ {@link DataSource}.</li>
 *   <li><b>Sự tồn tại của {@link OutboxWriter}:</b> chỉ ra rằng cơ chế outbox
 *       đã được khởi tạo đúng và sẵn sàng cho việc stage sự kiện.</li>
 * </ul>
 *
 * <p>Bean này được đăng ký với tên {@code "platform"} (xem {@link Component}
 * value) để tránh xung đột với các indicator mặc định của Spring Boot.</p>
 *
 * @author Family Tree Platform Team
 */
@Component("platform")
public class PlatformHealthIndicator implements HealthIndicator {

    /** Nguồn dữ liệu dùng để kiểm tra kết nối. */
    private final DataSource dataSource;

    /** Writer outbox dùng để xác nhận cơ chế transaction outbox đã sẵn sàng. */
    private final OutboxWriter outbox;

    /**
     * Khởi tạo indicator với {@link DataSource} và {@link OutboxWriter}.
     *
     * @param dataSource nguồn dữ liệu
     * @param outbox     writer outbox
     */
    public PlatformHealthIndicator(DataSource dataSource, OutboxWriter outbox) {
        this.dataSource = dataSource;
        this.outbox = outbox;
    }

    /**
     * Tính toán trạng thái sức khoẻ hiện tại của dịch vụ.
     *
     * <p>Quy trình:</p>
     * <ol>
     *   <li>Mở một connection từ {@link DataSource} (try-with-resources để đảm
     *       bảo đóng).</li>
     *   <li>Kiểm tra {@code isValid(2)} — JDBC driver sẽ chạy một truy vấn nhẹ
     *       với timeout 2 giây để xác nhận kết nối còn sống.</li>
     *   <li>Nếu hợp lệ: trả về {@link Health#up()} kèm các chi tiết.</li>
     *   <li>Nếu không hợp lệ: trả về {@link Health#down()} với lý do.</li>
     *   <li>Nếu ném exception (vd. DB không truy cập được): trả về DOWN kèm exception.</li>
     * </ol>
     *
     * @return {@link Health} mô tả trạng thái sức khoẻ hiện tại
     */
    @Override
    public Health health() {
        // Bước 1: Mở một connection ngắn hạn để kiểm tra kết nối. try-with-resources
        // đảm bảo connection được trả về pool ngay cả khi xảy ra exception.
        try (var c = dataSource.getConnection()) {
            // Bước 2: Kiểm tra kết nối còn hợp lệ hay không với timeout 2 giây.
            if (c.isValid(2)) {
                // Bước 3: Trả về UP kèm các chi tiết phục vụ debug — outbox_present
                // cho biết bean outbox đã được inject, checked_at để tra cứu thời điểm.
                return Health.up()
                        .withDetail("outbox_present", outbox != null)
                        .withDetails(Map.of("checked_at", Instant.now().toString()))
                        .build();
            }
            // Bước 4: Connection tồn tại nhưng không hợp lệ — DB có vấn đề.
            return Health.down().withDetail("reason", "db_unreachable").build();
        } catch (Exception e) {
            // Bước 5: Bắt mọi exception (timeout, network, ...) và trả về DOWN kèm exception.
            return Health.down(e).build();
        }
    }
}
