package com.familya.platform.inbox;

/**
 * Cổng (port) lưu trữ inbox cho cơ chế deduplication của consumer.
 *
 * <p>Quy trình sử dụng:</p>
 * <ol>
 *   <li>Trước khi xử lý một sự kiện, consumer gọi {@link #exists} để kiểm tra
 *       xem sự kiện đã được xử lý bởi consumer này hay chưa.</li>
 *   <li>Sau khi transaction cục bộ commit thành công, consumer gọi
 *       {@link #markProcessed} để đánh dấu sự kiện đã xử lý.</li>
 * </ol>
 *
 * <p><b>Vai trò của inbox:</b> Inbox là ranh giới an toàn cho cơ chế at-least-once —
 * consumer có thể nhận cùng một sự kiện nhiều lần (do retry, rebalance, ...),
 * nhưng inbox đảm bảo mỗi sự kiện chỉ được xử lý hiệu quả một lần. Các
 * handler downstream vẫn phải idempotent theo aggregate version để đảm bảo
 * an toàn trong trường hợp có sự cố giữa chừng.</p>
 *
 * @author Family Tree Platform Team
 */
public interface InboxStore {

    /**
     * Kiểm tra xem một sự kiện đã được consumer xử lý hay chưa.
     *
     * @param eventId  định danh duy nhất của sự kiện
     * @param consumer tên consumer đang xử lý
     * @return {@code true} nếu đã được xử lý, {@code false} nếu chưa
     */
    boolean exists(String eventId, String consumer);

    /**
     * Ghi nhận một sự kiện đã được xử lý bởi consumer.
     *
     * <p>Việc ghi nhận phải nằm trong cùng transaction với các thay đổi nghiệp
     * vụ để đảm bảo tính nguyên tử (atomic): không thể xảy ra trường hợp
     * nghiệp vụ đã thay đổi nhưng chưa đánh dấu inbox.</p>
     *
     * @param record bản ghi inbox cần ghi nhận
     */
    void markProcessed(InboxRecord record);
}
