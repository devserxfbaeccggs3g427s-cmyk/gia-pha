package com.familya.search.application.port.in;

import java.util.UUID;

/**
 * Truy vấn báo cáo gửi tới {@code ComputeReportUseCase}.
 *
 * <p>Báo cáo được xác định bởi {@code treeId} và {@code kind}. Client có thể
 * yêu cầu rằng báo cáo phải phản ánh một mức watermark tối thiểu thông qua
 * {@code requestedWatermark}. Nếu projection chưa đạt tới mức đó, use case
 * ném {@code StaleBarrierException}.</p>
 *
 * @param treeId               định danh cây gia phả.
 * @param actingUser           người dùng thực hiện truy vấn.
 * @param expectedTreeRevision phiên bản cây mà client kỳ vọng.
 * @param kind                 tên loại báo cáo (không phân biệt hoa/thường);
 *                             được chuyển thành {@link com.familya.search.domain.model.ReportSnapshot.Kind}.
 * @param requestedWatermark   watermark tối thiểu mà báo cáo phải đạt; nếu
 *                             {@code null}, use case sẽ trả về báo cáo với
 *                             watermark hiện tại của barrier.
 */
public record ReportQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String kind,
        Long requestedWatermark) {
}
