/**
 * Service truy vấn phía đọc cho các sự kiện audit.
 *
 * <p>Phân quyền được thực thi ở controller; service này chỉ trả lời
 * các truy vấn đọc.</p>
 */
package com.familya.auditops.application.usecase;

import com.familya.auditops.application.port.out.AuditAppender;
import com.familya.auditops.domain.model.AuditEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lớp service cung cấp các phương thức truy vấn audit event.
 *
 * <p>Tất cả các thao tác chạy trong transaction chỉ-đọc để tối ưu
 * hiệu năng và tránh ghi nhầm.</p>
 */
@Service
public class AuditQueryService {

    /** Port bền vững cho audit. */
    private final AuditAppender audit;

    /**
     * Khởi tạo service.
     *
     * @param audit port bền vững audit
     */
    public AuditQueryService(AuditAppender audit) {
        this.audit = audit;
    }

    /**
     * Trả về danh sách sự kiện audit của một operation.
     *
     * <p>{@code limit} được kẹp trong khoảng [1, 1000] để tránh truy
     * vấn quá lớn gây quá tải DB.</p>
     *
     * @param operationId id operation
     * @param limit       số lượng tối đa
     * @return danh sách sự kiện audit
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> byOperation(UUID operationId, int limit) {
        return audit.findByOperation(operationId, Math.min(Math.max(limit, 1), 1000));
    }

    /**
     * Tìm một sự kiện audit theo id.
     *
     * @param auditId id audit
     * @return sự kiện audit
     * @throws com.familya.platform.error.NotFoundException nếu không tồn tại
     */
    @Transactional(readOnly = true)
    public AuditEvent byId(UUID auditId) {
        return audit.findById(auditId).orElseThrow(() ->
                new com.familya.platform.error.NotFoundException("Audit " + auditId + " not found"));
    }
}