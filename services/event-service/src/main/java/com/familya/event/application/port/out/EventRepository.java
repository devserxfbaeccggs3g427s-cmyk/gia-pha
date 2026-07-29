package com.familya.event.application.port.out;

import com.familya.event.domain.model.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository {

    void insert(DomainEvent event);

    Optional<DomainEvent> findById(UUID id);

    List<DomainEvent> listByTree(UUID treeId, boolean includeTombstoned);

    void update(DomainEvent event);

    /** Non-tombstoned events whose primary or additional member list references {@code memberId}. */
    List<DomainEvent> listReferencingMember(UUID treeId, UUID memberId);

    /** Persist compensation snapshot for the delete-member Saga (best-effort, idempotent). */
    void saveCompensationSnapshot(UUID operationId, String snapshotJson);

    String loadCompensationSnapshot(UUID operationId);

    /**
     * Restore primary/additional member references that were cleared by the
     * detach step. Returns the number of events touched. Default returns 0 so
     * the interface stays binary-compatible.
     */
    default int restoreMemberReferences(UUID operationId, UUID memberId) { return 0; }

    /** Bulk tombstone every non-tombstoned event in the tree. */
    default int bulkTombstoneByTree(UUID treeId, Instant at) {
        int n = 0;
        for (DomainEvent ev : listByTree(treeId, false)) {
            ev.tombstone(ev.version(), at);
            update(ev);
            n++;
        }
        return n;
    }
}