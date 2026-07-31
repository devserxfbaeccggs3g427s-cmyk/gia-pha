/**
 * Adapter phía đọc (read-side) cho controller {@code OperationController}
 * dùng chung của platform.
 *
 * <p>Adapter này cho phép các controller dùng chung của platform có thể
 * truy vấn operation thông qua {@link com.familya.platform.api.OperationQuery}
 * mà không cần biết về {@code OperationRepository} cục bộ. Dữ liệu trả
 * về lấy từ projection {@code operation_audit} của audit-ops.</p>
 */
package com.familya.auditops.adapter.in.rest;

import com.familya.auditops.application.usecase.OperationService;
import com.familya.platform.api.AsyncOperation;
import com.familya.platform.api.OperationQuery;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai {@link OperationQuery} sử dụng {@link OperationService}
 * làm backend. Nếu operation không tồn tại, adapter trả về
 * {@link Optional#empty()} thay vì ném exception để tuân thủ hợp đồng
 * của interface.
 */
@Component
public class OperationProjectionAdapter implements OperationQuery {

    /** Service nghiệp vụ để truy vấn operation. */
    private final OperationService service;

    /**
     * Khởi tạo adapter với service bắt buộc.
     *
     * @param service service nghiệp vụ
     */
    public OperationProjectionAdapter(OperationService service) {
        this.service = service;
    }

    /**
     * Tìm operation theo id và chuyển sang {@link AsyncOperation}.
     *
     * <p>Quy trình:</p>
     * <ol>
     *   <li>Gọi {@link OperationService#find} để lấy operation.</li>
     *   <li>Nếu service ném {@code NotFoundException}, bắt lại và trả về
     *       {@code Optional.empty()} để giữ hợp đồng của {@link OperationQuery}.</li>
     * </ol>
     *
     * @param operationId UUID của operation cần tìm
     * @return {@link Optional} chứa {@link AsyncOperation} hoặc rỗng
     */
    @Override
    public Optional<AsyncOperation> findById(UUID operationId) {
        try {
            return Optional.of(service.find(operationId));
        } catch (com.familya.platform.error.NotFoundException e) {
            return Optional.empty();
        }
    }
}