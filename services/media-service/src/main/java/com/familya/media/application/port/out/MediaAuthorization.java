package com.familya.media.application.port.out;

import java.util.UUID;

/**
 * Authorization decision port. The implementation must consult the
 * local authorization projection populated from the membership topic.
 * Absent or revoked rows deny unsafe mutations.
 */
public interface MediaAuthorization {

    Decision authorize(UUID treeId, UUID userId, long expectedRevision);

    enum State { ALLOW, DENY, ABSENT, STALE }

    record Decision(State state, String reason) {
        public boolean isAllowed() { return state == State.ALLOW; }
    }
}
