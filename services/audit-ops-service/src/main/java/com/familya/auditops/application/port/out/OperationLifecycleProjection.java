/**
 * Port truy vấn và cập nhật projection vòng đời operation.
 *
 * <p>Projection này được dùng cho operator UI và replay lịch sử; nó
 * không phải nguồn dữ liệu nghiệp vụ. Mỗi row tương ứng với một
 * operation đang được các bounded context khác sở hữu.</p>
 */
package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.model.OperationLifecycleRow;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface cung cấp các thao tác CRUD đơn giản trên projection vòng đời.
 *
 * <p>Các phương thức {@code upsertStarted} và {@code applyStateChange}
 * nhận thêm {@code lastEventId} để hỗ trợ debug và replay.</p>
 */
public interface OperationLifecycleProjection {

    /**
     * Chèn hoặc cập nhật row khi nhận sự kiện {@code OperationStarted}.
     *
     * @param row         dữ liệu vòng đời
     * @param lastEventId id sự kiện Kafka nguồn
     */
    void upsertStarted(OperationLifecycleRow row, String lastEventId);

    /**
     * Cập nhật row khi nhận sự kiện thay đổi trạng thái.
     *
     * @param row         dữ liệu vòng đời
     * @param finalizedAt thời điểm kết thúc (nếu có)
     * @param lastEventId id sự kiện Kafka nguồn
     */
    void applyStateChange(OperationLifecycleRow row, Instant finalizedAt, String lastEventId);

    /**
     * Tìm row theo id operation.
     *
     * @param operationId id operation
     * @return {@link Optional} chứa row nếu tồn tại
     */
    Optional<OperationLifecycleRow> find(UUID operationId);
}