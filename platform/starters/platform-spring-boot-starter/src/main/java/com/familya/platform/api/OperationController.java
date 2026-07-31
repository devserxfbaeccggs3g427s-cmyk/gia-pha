package com.familya.platform.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Bộ điều khiển REST phía đọc cho envelope thao tác bất đồng bộ.
 *
 * <p>Mỗi dịch vụ triển khai port {@link OperationQuery} dựa trên projection
 * cục bộ của mình; controller này là lớp chia sẻ nhằm đảm bảo hợp đồng
 * endpoint công khai ({@code GET /api/v2/operations/{operationId}}) là đồng
 * nhất giữa tất cả các dịch vụ trong nền tảng.</p>
 *
 * <p>Endpoint này được client sử dụng để polling trạng thái của một thao tác
 * bất đồng bộ đã được chấp nhận trước đó (HTTP 202). Khi thao tác chuyển sang
 * trạng thái kết thúc (SUCCEEDED/FAILED/...), client dừng polling và xử lý kết quả.</p>
 *
 * @author Family Tree Platform Team
 */
@RestController
@RequestMapping("/api/v2/operations")
public class OperationController {

    /** Port đọc thông tin thao tác bất đồng bộ do dịch vụ sở hữu cung cấp. */
    private final OperationQuery query;

    /**
     * Khởi tạo controller với một {@link OperationQuery}.
     *
     * @param query cổng đọc thao tác bất đồng bộ của dịch vụ
     */
    public OperationController(OperationQuery query) {
        this.query = query;
    }

    /**
     * Xử lý yêu cầu {@code GET /api/v2/operations/{operationId}}.
     *
     * <p>Quy trình xử lý:</p>
     * <ol>
     *   <li>Truy vấn projection cục bộ thông qua {@link OperationQuery}.</li>
     *   <li>Nếu tìm thấy, trả về HTTP 200 cùng envelope {@link AsyncOperation}.</li>
     *   <li>Nếu không tìm thấy, trả về HTTP 404 với body rỗng.</li>
     * </ol>
     *
     * @param operationId định danh của thao tác cần truy vấn
     * @return {@link ResponseEntity} chứa envelope thao tác hoặc phản hồi 404
     */
    @GetMapping("/{operationId}")
    public ResponseEntity<AsyncOperation> get(@PathVariable UUID operationId) {
        // Bước 1: Ủy quyền việc tìm kiếm cho port query. Port này được dịch vụ
        // triển khai dựa trên projection cục bộ (MySQL/Redis tuỳ cấu hình).
        return query.findById(operationId)
                // Bước 2: Nếu tìm thấy, đóng gói trong HTTP 200 và trả về client.
                .map(op -> ResponseEntity.status(HttpStatus.OK).body(op))

                // Bước 3: Nếu không tìm thấy, trả về HTTP 404 với body rỗng để
                // client phân biệt với lỗi hệ thống.
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
