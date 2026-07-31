package com.familya.search.application.port.in;

import java.util.UUID;

/**
 * Truy vấn thống kê gửi tới {@code ComputeStatisticsUseCase}.
 *
 * <p>Cho phép client yêu cầu rằng kết quả phải phản ánh một mức watermark tối
 * thiểu. Nếu barrier chưa đạt, use case ném {@code StaleBarrierException}.</p>
 *
 * @param treeId               định danh cây gia phả.
 * @param actingUser           người dùng đang thực hiện truy vấn.
 * @param expectedTreeRevision phiên bản cây mà client kỳ vọng.
 * @param requestedWatermark   watermark tối thiểu yêu cầu, hoặc {@code null}
 *                             để nhận kết quả tại barrier hiện tại.
 */
public record StatisticsQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        Long requestedWatermark) {
}
