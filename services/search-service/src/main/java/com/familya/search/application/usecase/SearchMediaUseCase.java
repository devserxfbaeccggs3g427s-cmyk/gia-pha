package com.familya.search.application.usecase;

import com.familya.search.application.port.in.SearchMediaQuery;
import com.familya.search.application.port.out.MediaSearchRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.domain.model.MediaSearchDocument;
import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.normalizer.VietnameseNormalizer;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SearchMediaUseCase {

    private final SearchAuthorization authz;
    private final MediaSearchRepository repo;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    public SearchMediaUseCase(SearchAuthorization authz, MediaSearchRepository repo,
                                SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.watermark = watermark;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public Result execute(SearchMediaQuery q) {
        SearchAuthorization.Decision d = authz.authorize(q.treeId(), q.actingUser(), q.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot search media: " + d.reason());
        }
        String normalized = VietnameseNormalizer.normalize(q.q());
        int limit = q.limit() == null ? 50 : Math.min(q.limit(), 500);
        var filter = new com.familya.search.application.port.in.SearchMediaCommand.MediaFilter(q.kind(), q.tombstoned());
        List<MediaSearchDocument> docs = repo.search(filter, normalized, q.treeId(), limit);
        metrics.mutationAccepted("search-service", "searchMedia");
        return new Result(docs, watermark.barrierFor(q.treeId()));
    }

    public record Result(List<MediaSearchDocument> docs, RevisionBarrier barrier) { }
}
