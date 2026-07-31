package com.familya.platform.projection;

import com.familya.platform.error.StaleProjectionException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * SDK uỷ quyền dựa trên projection — tái sử dụng được cho mọi dịch vụ.
 *
 * <p>SDK thực hiện bốn kiểm tra bắt buộc trước khi cho phép một hành động:</p>
 * <ol>
 *   <li><b>Kiểm tra sự tồn tại:</b> Nếu không có hàng projection, fail closed
 *       đối với mutation không an toàn; các sensitive read có thể dùng RPC
 *       khẩn cấp đến dịch vụ nguồn với deadline {@code emergencyRpcBudget}.</li>
 *   <li><b>Kiểm tra revision/epoch:</b> Nếu revision của projection thấp hơn
 *       phiên bản mà caller mong đợi, fail closed.</li>
 *   <li><b>Kiểm tra độ tươi:</b> Nếu {@code lastUpdatedAt} vượt quá
 *       {@code freshnessThreshold}, fail closed cho mutation không an toàn.</li>
 *   <li><b>Kiểm tra thu hồi:</b> Nếu hàng đã bị thu hồi, từ chối ngay lập tức.</li>
 * </ol>
 *
 * <p><b>Nguyên tắc thiết kế:</b> SDK được thiết kế độc lập với framework.
 * Mỗi dịch vụ cung cấp {@link ProjectionReader} riêng; SDK tổng hợp chính
 * sách. Các dịch vụ KHÔNG ĐƯỢC tự thay thế SDK bằng kiểm tra ad-hoc vì SDK
 * chính là nguồn sự thật (single source of truth) cho mục tiêu thu hồi.</p>
 *
 * @author Family Tree Platform Team
 */
public final class AuthorizationProjection {

    /** Reader do dịch vụ cung cấp, dùng để truy vấn projection cục bộ. */
    private final ProjectionReader reader;

    /** Ngưỡng thời gian tối đa cho phép giữa hai lần cập nhật projection. */
    private final Duration freshnessThreshold;

    /** Budget (deadline) cho phép khi cần gọi RPC khẩn cấp đến dịch vụ nguồn. */
    private final Duration emergencyRpcBudget;

    /**
     * Khởi tạo SDK với reader và các ngưỡng chính sách.
     *
     * @param reader              reader projection của dịch vụ
     * @param freshnessThreshold  ngưỡng thời gian cho phép trước khi projection bị coi là cũ
     * @param emergencyRpcBudget  deadline tối đa cho phép khi gọi RPC khẩn cấp
     */
    public AuthorizationProjection(ProjectionReader reader,
                                   Duration freshnessThreshold,
                                   Duration emergencyRpcBudget) {
        this.reader = reader;
        this.freshnessThreshold = freshnessThreshold;
        this.emergencyRpcBudget = emergencyRpcBudget;
    }

    /**
     * Thực hiện chuỗi kiểm tra uỷ quyền cho một aggregate cụ thể.
     *
     * @param aggregateId      định danh aggregate cần kiểm tra
     * @param userId           định danh người dùng thực hiện yêu cầu
     * @param expectedRevision revision tối thiểu mà caller kỳ vọng
     * @param rowType          kiểu dữ liệu projection mong muốn
     * @param <T>              kiểu projection row
     * @return {@link Decision} mô tả kết quả uỷ quyền
     * @throws StaleProjectionException nếu revision của projection thấp hơn
     *         phiên bản caller mong đợi — đây là tín hiệu "dữ liệu chắc chắn cũ"
     */
    public <T extends ProjectionRow> Decision<T> authorize(java.util.UUID aggregateId,
                                                            java.util.UUID userId,
                                                            long expectedRevision,
                                                            Class<T> rowType) {
        // Bước 1: Tìm hàng projection trong kho lưu trữ cục bộ.
        Optional<T> row = reader.find(aggregateId, userId, rowType);

        // Bước 2: Nếu không tìm thấy — fail closed cho mutation không an toàn.
        // Caller có thể quyết định gọi RPC khẩn cấp với deadline emergencyRpcBudget.
        if (row.isEmpty()) {
            return Decision.absent();
        }

        // Bước 3: Nếu hàng đã bị thu hồi, từ chối ngay lập tức.
        T r = row.get();
        if (r.isRevoked()) {
            return Decision.deny(r, "revoked");
        }

        // Bước 4: Kiểm tra revision — nếu thấp hơn kỳ vọng, ném ngoại lệ
        // để caller xử lý theo chính sách của mình (thường là load lại dữ liệu).
        if (r.header().revision() < expectedRevision) {
            throw new StaleProjectionException(
                    "Projection revision " + r.header().revision() + " < expected " + expectedRevision);
        }

        // Bước 5: Kiểm tra độ tươi. Nếu quá cũ, trả về STALE để caller quyết định
        // có gọi RPC khẩn cấp hay không (đặc biệt cho sensitive read).
        if (Duration.between(r.header().lastUpdatedAt(), Instant.now()).compareTo(freshnessThreshold) > 0) {
            return Decision.stale(r, freshnessThreshold);
        }

        // Bước 6: Mọi kiểm tra đều thỏa mãn — cho phép thực hiện hành động.
        return Decision.allow(r);
    }

    /** @return deadline tối đa cho phép khi gọi RPC khẩn cấp */
    public Duration emergencyRpcBudget() { return emergencyRpcBudget; }

    /** @return ngưỡng freshness hiện đang dùng */
    public Duration freshnessThreshold() { return freshnessThreshold; }

    /** @return reader projection hiện đang dùng */
    public ProjectionReader reader() { return reader; }

    /**
     * Interface mà mỗi dịch vụ triển khai để cung cấp khả năng truy vấn projection cục bộ.
     */
    public interface ProjectionReader {
        /**
         * Tìm hàng projection cho aggregate và user.
         *
         * @param aggregateId định danh aggregate
         * @param userId      định danh người dùng
         * @param rowType     kiểu projection row mong muốn
         * @param <T>         kiểu projection row
         * @return {@link Optional} chứa hàng nếu tồn tại
         */
        <T extends ProjectionRow> Optional<T> find(java.util.UUID aggregateId,
                                                  java.util.UUID userId,
                                                  Class<T> rowType);
    }

    /**
     * Hợp đồng tối thiểu cho một hàng projection. Mỗi projection cụ thể sẽ
     * triển khai interface này và bổ sung các trường riêng.
     */
    public interface ProjectionRow {
        /** @return header chứa metadata của hàng projection */
        ProjectionHeader header();

        /** @return {@code true} nếu hàng đã bị thu hồi */
        boolean isRevoked();
    }

    /**
     * Kết quả của một lần uỷ quyền.
     *
     * @param row    hàng projection (có thể null nếu ABSENT)
     * @param state  trạng thái quyết định
     * @param reason mô tả ngắn gọn lý do
     * @param <T>    kiểu projection row
     */
    public record Decision<T>(T row, State state, String reason) {
        /** Tập trạng thái quyết định. */
        public enum State { ALLOW, DENY, ABSENT, STALE }

        /** @return {@code true} nếu được phép */
        public boolean isAllowed() { return state == State.ALLOW; }
        /** @return {@code true} nếu bị từ chối */
        public boolean isDenied() { return state == State.DENY; }
        /** @return {@code true} nếu không có hàng */
        public boolean isAbsent() { return state == State.ABSENT; }
        /** @return {@code true} nếu dữ liệu quá cũ */
        public boolean isStale() { return state == State.STALE; }

        /** @param row hàng projection đã qua kiểm tra */
        public static <T> Decision<T> allow(T row) { return new Decision<>(row, State.ALLOW, "ok"); }

        /** @param row hàng projection bị từ chối */
        /** @param why lý do từ chối */
        public static <T> Decision<T> deny(T row, String why) { return new Decision<>(row, State.DENY, why); }

        /** @return decision ABSENT đại diện cho việc không có hàng projection */
        public static <T> Decision<T> absent() { return new Decision<>(null, State.ABSENT, "absent"); }

        /** @param row hàng projection bị coi là cũ */
        /** @param threshold ngưỡng freshness bị vượt */
        public static <T> Decision<T> stale(T row, Duration threshold) {
            return new Decision<>(row, State.STALE, "older than " + threshold.toSeconds() + "s");
        }
    }
}
