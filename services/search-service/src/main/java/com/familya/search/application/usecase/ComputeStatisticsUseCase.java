package com.familya.search.application.usecase;

import com.familya.search.application.port.in.StatisticsQuery;
import com.familya.search.application.port.out.SearchAuthorization;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.application.port.out.StatisticsRepository;
import com.familya.search.domain.exception.StaleBarrierException;
import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.model.StatisticsSnapshot;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case tính (hoặc tính lại) thống kê của một cây gia phả.
 *
 * <p>Luồng xử lý:</p>
 * <ol>
 *   <li>Kiểm tra quyền truy cập.</li>
 *   <li>Đọc barrier và xác định watermark nhỏ nhất (mức mà mọi miền đều đã hội tụ).</li>
 *   <li>Nếu client yêu cầu watermark cao hơn - ném {@link StaleBarrierException}.</li>
 *   <li>Uỷ quyền cho {@code StatisticsRepository} đếm và ghi nhớ bản thống kê.</li>
 *   <li>Ghi nhận metric và trả kết quả kèm barrier.</li>
 * </ol>
 */
@Service
public class ComputeStatisticsUseCase {

    private final SearchAuthorization authz;
    private final StatisticsRepository repo;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case với các phụ thuộc bắt buộc.
     *
     * @param authz     cổng kiểm tra quyền.
     * @param repo      cổng tính/đọc thống kê.
     * @param watermark cổng đọc barrier phiên bản.
     * @param metrics   cổng ghi nhận telemetry.
     */
    public ComputeStatisticsUseCase(SearchAuthorization authz, StatisticsRepository repo,
                                     SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.watermark = watermark;
        this.metrics = metrics;
    }

    /**
     * Thực thi tính thống kê.
     *
     * @param q truy vấn chứa cây, người dùng, phiên bản và watermark tối thiểu (tuỳ chọn).
     * @return kết quả gồm {@link StatisticsSnapshot} và barrier.
     * @throws ForbiddenException    nếu client không có quyền.
     * @throws StaleBarrierException nếu barrier chưa đạt watermark yêu cầu.
     */
    @Transactional
    public Result execute(StatisticsQuery q) {
        SearchAuthorization.Decision d = authz.authorize(q.treeId(), q.actingUser(), q.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot compute statistics: " + d.reason());
        }
        RevisionBarrier barrier = watermark.barrierFor(q.treeId());
        long watermarkValue = min(barrier);
        if (q.requestedWatermark() != null && watermarkValue < q.requestedWatermark()) {
            throw new StaleBarrierException(
                    "Statistics watermark " + watermarkValue + " < requested " + q.requestedWatermark());
        }
        var snap = repo.compute(q.treeId(), watermarkValue);
        metrics.mutationAccepted("search-service", "computeStatistics");
        return new Result(snap, barrier);
    }

    /**
     * Tính watermark nhỏ nhất trong barrier.
     *
     * @param barrier rào chắn phiên bản.
     * @return giá trị nhỏ nhất; {@code 0L} nếu barrier rỗng.
     */
    private long min(RevisionBarrier barrier) {
        long min = Long.MAX_VALUE;
        for (Long v : barrier.values().values()) {
            if (v != null && v < min) min = v;
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    /**
     * Kết quả trả về của use case.
     *
     * @param snapshot bản thống kê vừa tính.
     * @param barrier  barrier phiên bản để trả về header cho client.
     */
    public record Result(StatisticsSnapshot snapshot, RevisionBarrier barrier) { }
}
