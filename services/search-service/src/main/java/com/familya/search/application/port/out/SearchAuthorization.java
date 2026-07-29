package com.familya.search.application.port.out;

import java.util.UUID;

public interface SearchAuthorization {
    Decision authorize(UUID treeId, UUID userId, long expectedRevision);

    enum State { ALLOW, DENY, ABSENT, STALE }

    record Decision(State state, String reason) {
        public boolean isAllowed() { return state == State.ALLOW; }
    }
}
