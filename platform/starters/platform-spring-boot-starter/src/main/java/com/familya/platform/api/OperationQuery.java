package com.familya.platform.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) đọc cho projection của thao tác bất đồng bộ.
 *
 * <p>Dịch vụ sở hữu cung cấp một adapter (tham khảo
 * {@code platform/observability/OperationProjection.java} trong audit-ops reference)
 * để controller chia sẻ {@link OperationController} có thể trả lời các yêu
 * cầu polling một cách thống nhất trên toàn nền tảng.</p>
 *
 * <p>Thiết kế theo nguyên tắc hexagonal architecture: controller phụ thuộc
 * vào interface này thay vì vào một triển khai cụ thể, giúp dễ dàng thay
 * thế nguồn dữ liệu (MySQL, Redis, cache trong bộ nhớ, ...) mà không ảnh
 * hưởng đến lớp trình bày.</p>
 *
 * @author Family Tree Platform Team
 */
public interface OperationQuery {

    /**
     * Tìm kiếm thao tác bất đồng bộ theo định danh.
     *
     * @param operationId định danh của thao tác cần truy vấn
     * @return {@link Optional} chứa envelope nếu tìm thấy, {@link Optional#empty()} nếu không
     */
    Optional<AsyncOperation> findById(UUID operationId);
}
