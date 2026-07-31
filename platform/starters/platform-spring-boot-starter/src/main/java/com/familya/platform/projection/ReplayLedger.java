package com.familya.platform.projection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sổ cái (ledger) phục vụ replay và đối chiếu projection.
 *
 * <p>Mỗi dịch vụ ghi thêm một mục cho mỗi chuyển tiếp watermark quan sát được
 * từ một topic nguồn. Ledger trả lời hai câu hỏi quan trọng:</p>
 * <ol>
 *   <li><b>Revision cao nhất mà consumer này đã thừa nhận?</b> — thúc đẩy
 *       kiểm tra freshness trong {@link AuthorizationProjection}.</li>
 *   <li><b>Có khoảng trống (gap) không?</b> — Nếu {@code lastSeenOffset} của
 *       một mục không bằng {@code lastSeenOffset + 1} của mục trước cho cùng
 *       partition, consumer phải kích hoạt replay.</li>
 * </ol>
 *
 * <p><b>Nguyên tắc thiết kế:</b> Ledger được thiết kế độc lập với framework.
 * Mỗi consumer lưu trữ hàng ledger trong MySQL cục bộ; pipeline đối chiếu
 * đọc ledger xuyên suốt các dịch vụ để tính toán cutover gate toàn cục.</p>
 *
 * @author Family Tree Platform Team
 */
public final class ReplayLedger {

    /** Ngăn việc khởi tạo — lớp chỉ cung cấp phương thức tĩnh. */
    private ReplayLedger() { }

    /**
     * Tạo một mục ledger mới với {@code recordedAt} là thời điểm hiện tại.
     *
     * @param aggregateId        định danh aggregate được cập nhật
     * @param topic              topic nguồn
     * @param partition          số partition
     * @param offset             offset cuối cùng đã thấy
     * @param aggregateRevision  revision aggregate nguồn
     * @param epoch              epoch aggregate nguồn
     * @return {@link Entry} mới
     */
    public static Entry newEntry(UUID aggregateId, String topic, int partition,
                                  long offset, long aggregateRevision, long epoch) {
        // Tạo Entry với thời điểm ghi nhận là Instant.now() để phản ánh chính xác lúc xử lý.
        return new Entry(aggregateId, topic, partition, offset, aggregateRevision, epoch,
                java.time.Instant.now());
    }

    /**
     * Chuyển đổi một {@link Entry} sang {@link Map} để dễ dàng lưu trữ hoặc serialize.
     *
     * <p>Sử dụng {@link LinkedHashMap} để giữ thứ tự các trường — thuận tiện
     * cho việc đọc log hoặc debug.</p>
     *
     * @param e entry cần chuyển đổi
     * @return {@link Map} với các trường của entry
     */
    public static Map<String, Object> toMap(Entry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        // aggregateId được chuyển sang chuỗi UUID để tương thích với nhiều định dạng lưu trữ.
        m.put("aggregateId", e.aggregateId().toString());
        m.put("topic", e.topic());
        m.put("partition", e.partition());
        m.put("lastSeenOffset", e.lastSeenOffset());
        m.put("aggregateRevision", e.aggregateRevision());
        m.put("epoch", e.epoch());
        m.put("recordedAt", e.recordedAt().toString());
        return m;
    }

    /**
     * Bản ghi ledger cho một lần quan sát watermark.
     *
     * @param aggregateId       định danh aggregate
     * @param topic             topic Kafka nguồn
     * @param partition         số partition
     * @param lastSeenOffset    offset cuối cùng đã thấy trong partition
     * @param aggregateRevision revision aggregate nguồn
     * @param epoch             epoch aggregate nguồn
     * @param recordedAt        thời điểm ghi nhận
     */
    public record Entry(
            UUID aggregateId,
            String topic,
            int partition,
            long lastSeenOffset,
            long aggregateRevision,
            long epoch,
            java.time.Instant recordedAt
    ) { }
}
