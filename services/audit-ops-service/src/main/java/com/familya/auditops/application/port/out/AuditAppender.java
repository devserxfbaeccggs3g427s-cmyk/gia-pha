/**
 * Port chỉ-append cho nhật ký kiểm toán (audit log).
 *
 * <p>Các triển khai <b>BẮT BUỘC</b> phải từ chối mọi thao tác cập
 * nhật hoặc xoá. Các thao tác đọc chỉ phục vụ cho operator và đánh
 * giá bảo mật.</p>
 */
package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.model.AuditEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface định nghĩa các thao tác ghi/đọc audit log.
 *
 * <p>Đây là một port trong kiến trúc hexagonal; triển khai cụ thể nằm
 * trong package adapter (xem {@code JdbcAuditAppender}).</p>
 */
public interface AuditAppender {

    /**
     * Thêm một sự kiện audit vào log.
     *
     * @param event sự kiện cần ghi
     * @return sự kiện đã ghi (giữ nguyên tham chiếu)
     */
    AuditEvent append(AuditEvent event);

    /**
     * Tìm các sự kiện audit của một operation, sắp xếp theo thời gian
     * giảm dần.
     *
     * @param operationId id operation
     * @param limit       số lượng tối đa
     * @return danh sách sự kiện
     */
    List<AuditEvent> findByOperation(UUID operationId, int limit);

    /**
     * Tìm một sự kiện audit theo id.
     *
     * @param auditId id audit
     * @return {@link Optional} chứa sự kiện nếu tồn tại
     */
    Optional<AuditEvent> findById(UUID auditId);
}