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

@Service
public class ComputeStatisticsUseCase {

    private final SearchAuthorization authz;
    private final StatisticsRepository repo;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    public ComputeStatisticsUseCase(SearchAuthorization authz, StatisticsRepository repo,
                                     SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.watermark = watermark;
        this.metrics = metrics;
    }

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

    private long min(RevisionBarrier barrier) {
        long min = Long.MAX_VALUE;
        for (Long v : barrier.values().values()) {
            if (v != null && v < min) min = v;
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    public record Result(StatisticsSnapshot snapshot, RevisionBarrier barrier) { }
}
