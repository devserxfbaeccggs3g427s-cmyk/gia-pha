package com.familya.search.application.usecase;

import com.familya.search.application.port.in.ReportQuery;
import com.familya.search.application.port.out.ReportRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.domain.exception.StaleBarrierException;
import com.familya.search.domain.model.ReportSnapshot;
import com.familya.search.domain.model.RevisionBarrier;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ComputeReportUseCase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SearchAuthorization authz;
    private final ReportRepository repo;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    public ComputeReportUseCase(SearchAuthorization authz, ReportRepository repo,
                                  SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.watermark = watermark;
        this.metrics = metrics;
    }

    @Transactional
    public Result execute(ReportQuery q) {
        SearchAuthorization.Decision d = authz.authorize(q.treeId(), q.actingUser(), q.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot compute report: " + d.reason());
        }
        RevisionBarrier barrier = watermark.barrierFor(q.treeId());
        long watermarkValue = min(barrier);
        if (q.requestedWatermark() != null && watermarkValue < q.requestedWatermark()) {
            throw new StaleBarrierException(
                    "Report watermark " + watermarkValue + " < requested " + q.requestedWatermark());
        }
        ReportSnapshot.Kind kind = ReportSnapshot.Kind.valueOf(q.kind().toUpperCase());
        if (repo.exists(q.treeId(), kind, watermarkValue)) {
            // deterministic for the same watermark
            var existing = repo.findById(UUID.nameUUIDFromBytes(
                    (q.treeId() + ":" + kind + ":" + watermarkValue).getBytes()));
            return new Result(existing.orElseGet(() -> synthesize(q.treeId(), kind, watermarkValue)), barrier);
        }
        ReportSnapshot snap = synthesize(q.treeId(), kind, watermarkValue);
        repo.save(snap);
        metrics.mutationAccepted("search-service", "computeReport");
        return new Result(snap, barrier);
    }

    private ReportSnapshot synthesize(UUID treeId, ReportSnapshot.Kind kind, long watermark) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", treeId.toString());
        payload.put("kind", kind.name());
        payload.put("watermark", watermark);
        payload.put("generatedAt", Instant.now().toString());
        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize report payload", e);
        }
        return new ReportSnapshot(UUID.randomUUID(), treeId, kind, json, watermark, Instant.now());
    }

    private long min(RevisionBarrier barrier) {
        long min = Long.MAX_VALUE;
        for (Long v : barrier.values().values()) {
            if (v != null && v < min) min = v;
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    public record Result(ReportSnapshot report, RevisionBarrier barrier) { }
}
