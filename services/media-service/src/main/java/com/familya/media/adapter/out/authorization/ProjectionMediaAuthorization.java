package com.familya.media.adapter.out.authorization;

import com.familya.media.application.port.out.MediaAuthRepository;
import com.familya.media.application.port.out.MediaAuthorization;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProjectionMediaAuthorization implements MediaAuthorization {

    private final MediaAuthRepository repo;

    public ProjectionMediaAuthorization(MediaAuthRepository repo) {
        this.repo = repo;
    }

    @Override
    public Decision authorize(UUID treeId, UUID userId, long expectedRevision) {
        Optional<MediaAuthRepository.MediaAuthRow> row = repo.findAuth(treeId, userId);
        if (row.isEmpty()) {
            return new Decision(State.ABSENT, "no projection row");
        }
        var r = row.get();
        if (r.isRevoked()) {
            return new Decision(State.DENY, "revoked");
        }
        if (!r.canEdit()) {
            return new Decision(State.DENY, "role=" + r.role());
        }
        if (r.revision() < expectedRevision) {
            return new Decision(State.STALE, "projection revision " + r.revision() + " < expected " + expectedRevision);
        }
        return new Decision(State.ALLOW, "ok");
    }
}
