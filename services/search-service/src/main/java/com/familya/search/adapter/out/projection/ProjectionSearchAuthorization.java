package com.familya.search.adapter.out.projection;

import com.familya.search.application.port.out.SearchAuthRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProjectionSearchAuthorization implements SearchAuthorization {

    private final SearchAuthRepository repo;

    public ProjectionSearchAuthorization(SearchAuthRepository repo) {
        this.repo = repo;
    }

    @Override
    public Decision authorize(UUID treeId, UUID userId, long expectedRevision) {
        Optional<SearchAuthRepository.AuthRow> row = repo.find(treeId, userId);
        if (row.isEmpty()) {
            return new Decision(State.ABSENT, "no projection row");
        }
        var r = row.get();
        if (r.isRevoked()) {
            return new Decision(State.DENY, "revoked");
        }
        if (r.revision() < expectedRevision) {
            return new Decision(State.STALE, "projection revision " + r.revision() + " < expected " + expectedRevision);
        }
        return new Decision(State.ALLOW, "ok");
    }
}
