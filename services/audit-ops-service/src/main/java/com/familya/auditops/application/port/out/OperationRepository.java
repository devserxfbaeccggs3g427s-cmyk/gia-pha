/**
 * Port bền vững cho projection operation.
 *
 * <p>Các thao tác đọc được phục vụ trực tiếp bởi database; thao tác
 * ghi commit trạng thái mới và một outbox row tương ứng trong cùng
 * một transaction cục bộ.</p>
 */
package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.model.Operation;
import com.familya.auditops.domain.model.OperationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface cung cấp các thao tác CRUD và chuyển trạng thái trên
 * projection {@code operation_audit}.
 */
public interface OperationRepository {

    /**
     * Chèn một operation mới ở trạng thái {@code PENDING}.
     *
     * <p>Trả về aggregate đã được bền vững (bao gồm id được sinh và
     * version ban đầu).</p>
     *
     * @param operation operation cần chèn
     * @return operation đã lưu
     */
    Operation insert(Operation operation);

    /**
     * Tìm operation theo id.
     *
     * <p>Kết quả phục vụ cho polling và operator tooling; <b>không</b>
     * dùng cho quyết định nghiệp vụ hay phân quyền.</p>
     *
     * @param operationId id operation
     * @return {@link Optional} chứa operation nếu tồn tại
     */
    Optional<Operation> findById(UUID operationId);

    /**
     * Chuyển trạng thái operation theo cơ chế atomic.
     *
     * <p>Repository đọc row với {@code FOR UPDATE}, kiểm tra
     * {@code expectedVersion}, áp dụng mutation và tăng version.
     * Ném {@link com.familya.platform.error.OptimisticConcurrencyException}
     * nếu row đã bị thay đổi bởi writer khác.</p>
     *
     * @param operationId     id operation
     * @param expectedVersion version kỳ vọng
     * @param next            trạng thái mới
     * @param errorCode       mã lỗi (nếu có)
     * @param errorMessage    thông điệp lỗi (nếu có)
     * @param when            thời điểm áp dụng
     * @return operation đã cập nhật
     */
    Operation transition(UUID operationId,
                         long expectedVersion,
                         OperationStatus next,
                         String errorCode,
                         String errorMessage,
                         java.time.Instant when);

    /**
     * Bộ lọc do operator cung cấp. Dùng cho console vận hành.
     *
     * @param status trạng thái cần lọc
     * @param limit  số lượng tối đa
     * @return danh sách operation
     */
    List<Operation> findByStatus(OperationStatus status, int limit);

    /**
     * Đếm số operation theo tập trạng thái.
     *
     * <p>Dùng cho health và quy tắc cutover auto-stop.</p>
     *
     * @param statuses danh sách trạng thái
     * @return tổng số operation khớp
     */
    long countByStatusIn(List<OperationStatus> statuses);
}