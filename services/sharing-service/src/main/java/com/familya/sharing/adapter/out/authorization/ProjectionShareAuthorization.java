package com.familya.sharing.adapter.out.authorization;

import com.familya.sharing.application.port.out.ShareAuthRepository;
import com.familya.sharing.application.port.out.ShareAuthorization;
import com.familya.sharing.domain.model.ShareLink;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProjectionShareAuthorization implements ShareAuthorization {

    private final ShareAuthRepository repo;

    public ProjectionShareAuthorization(ShareAuthRepository repo) {
        this.repo = repo;
    }

    @Override
    public Decision authorize(UUID treeId, UUID userId, long expectedRevision) {
        var row = repo.find(treeId, userId);
        if (row.isEmpty()) {
            return new Decision(State.ABSENT, "no projection row");
        }
        var r = row.get();
        if (r.isRevoked()) {
            return new Decision(State.DENY, "revoked");
        }
        if (!r.canShare()) {
            return new Decision(State.DENY, "role=" + r.role());
        }
        if (r.revision() < expectedRevision) {
            return new Decision(State.STALE, "projection revision " + r.revision() + " < expected " + expectedRevision);
        }
        return new Decision(State.ALLOW, "ok");
    }

    @Override
    public ShareLink.Role defaultRole() {
        return ShareLink.Role.VIEWER;
    }
}
