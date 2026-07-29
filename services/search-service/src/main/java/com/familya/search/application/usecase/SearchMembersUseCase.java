package com.familya.search.application.usecase;

import com.familya.search.application.port.in.SearchMembersQuery;
import com.familya.search.application.port.out.MemberSearchRepository;
import com.familya.search.application.port.out.SearchAuthRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.domain.exception.StaleBarrierException;
import com.familya.search.domain.model.MemberSearchDocument;
import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.model.Watermark;
import com.familya.search.domain.normalizer.VietnameseNormalizer;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchMembersUseCase {

    private final SearchAuthorization authz;
    private final MemberSearchRepository repo;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    public SearchMembersUseCase(SearchAuthorization authz, MemberSearchRepository repo,
                                  SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.watermark = watermark;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public Result execute(SearchMembersQuery q) {
        SearchAuthorization.Decision d = authz.authorize(q.treeId(), q.actingUser(), q.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot search members: " + d.reason());
        }
        String normalized = VietnameseNormalizer.normalize(q.q());
        int limit = q.limit() == null ? 50 : Math.min(q.limit(), 500);
        var filter = new com.familya.search.application.port.in.SearchMembersCommand.MemberFilter(
                q.birthYear(), q.birthYearFrom(), q.birthYearTo(), q.tombstoned());
        List<MemberSearchDocument> docs = repo.search(filter, normalized, q.treeId(), limit);
        RevisionBarrier barrier = watermark.barrierFor(q.treeId());
        metrics.mutationAccepted("search-service", "searchMembers");
        return new Result(docs, barrier);
    }

    public record Result(List<MemberSearchDocument> docs, RevisionBarrier barrier) { }
}
