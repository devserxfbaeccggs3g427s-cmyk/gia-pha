package com.familya.search.application.usecase;

import com.familya.search.application.port.in.AutocompleteQuery;
import com.familya.search.application.port.out.AutocompleteRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import com.familya.search.domain.model.AutocompleteEntry;
import com.familya.search.domain.normalizer.VietnameseNormalizer;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AutocompleteUseCase {

    private final SearchAuthorization authz;
    private final AutocompleteRepository repo;
    private final PlatformMetrics metrics;

    public AutocompleteUseCase(SearchAuthorization authz, AutocompleteRepository repo, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public List<AutocompleteEntry> execute(AutocompleteQuery q) {
        SearchAuthorization.Decision d = authz.authorize(q.treeId(), q.actingUser(), q.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot autocomplete: " + d.reason());
        }
        int limit = q.limit() == null ? 20 : Math.min(q.limit(), 100);
        metrics.mutationAccepted("search-service", "autocomplete");
        return repo.suggestions(VietnameseNormalizer.normalize(q.prefix()), q.treeId(), limit);
    }
}
