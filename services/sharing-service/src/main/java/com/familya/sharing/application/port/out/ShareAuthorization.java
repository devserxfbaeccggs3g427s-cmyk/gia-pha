package com.familya.sharing.application.port.out;

import com.familya.sharing.domain.model.ShareLink;

import java.util.Optional;
import java.util.UUID;

public interface ShareAuthorization {
    Decision authorize(UUID treeId, UUID userId, long expectedRevision);

    enum State { ALLOW, DENY, ABSENT, STALE }

    record Decision(State state, String reason) {
        public boolean isAllowed() { return state == State.ALLOW; }
    }

    ShareLink.Role defaultRole();
}
