package vn.giapha.research.media.domain;

import java.util.Set;
import vn.giapha.research.media.shared.error.ConflictException;

/**
 * Media object lifecycle (design.md state machine):
 *
 * <pre>
 * PENDING_UPLOAD -> PENDING_SCAN -> ACTIVE -> DELETING
 *        |               |            |
 *        v               v            v
 *     FAILED          FAILED     RETENTION_HELD -> DELETED
 *        |
 *        v
 *    ORPHANED
 * </pre>
 */
public enum MediaStatus {
    PENDING_UPLOAD,
    PENDING_SCAN,
    ACTIVE,
    DELETING,
    FAILED,
    ORPHANED,
    RETENTION_HELD,
    DELETED;

    public Set<MediaStatus> allowedTransitions() {
        return switch (this) {
            case PENDING_UPLOAD -> Set.of(PENDING_SCAN, FAILED);
            case PENDING_SCAN -> Set.of(ACTIVE, FAILED);
            case ACTIVE -> Set.of(DELETING, RETENTION_HELD);
            case DELETING, RETENTION_HELD -> Set.of(DELETED);
            case FAILED -> Set.of(ORPHANED);
            case ORPHANED, DELETED -> Set.of();
        };
    }

    /** Validates a lifecycle transition, rejecting anything outside the state machine. */
    public MediaStatus transitionTo(MediaStatus next) {
        if (!allowedTransitions().contains(next)) {
            throw new ConflictException("Illegal media status transition " + this + " -> " + next);
        }
        return next;
    }
}
