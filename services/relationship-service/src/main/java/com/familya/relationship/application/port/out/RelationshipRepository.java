package com.familya.relationship.application.port.out;

import com.familya.relationship.domain.model.Relationship;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelationshipRepository {

    void insert(Relationship rel);

    Optional<Relationship> findById(UUID id);

    List<Relationship> listByTree(UUID treeId, boolean includeTombstoned);

    /** Active (non-tombstoned) edges that touch the member in either direction. */
    List<Relationship> listActiveByMember(UUID treeId, UUID memberId);

    /**
     * Returns the highest committed command sequence for the tree
     * (used by the per-tree serializer). Inside a {@code SELECT …
     * FOR UPDATE} so concurrent transactions serialise.
     */
    long nextCommandSeq(UUID treeId);

    void appendCommandLog(UUID treeId, long commandSeq, String commandType,
                          UUID actorUserId, String payloadHash, java.time.Instant committedAt);

    void update(Relationship rel);

    /** Compensation support: clear tombstoned_at to restore an edge. */
    void untombstone(UUID id, Instant at, long expectedVersion);

    boolean existsEdge(UUID treeId, Relationship.Kind kind, UUID from, UUID to);

    /** Persist compensation snapshot for the delete-member Saga (best-effort, idempotent). */
    void saveCompensationSnapshot(UUID operationId, String snapshotJson);

    String loadCompensationSnapshot(UUID operationId);

    /**
     * Bulk tombstone every non-tombstoned relationship in the tree.
     * Default iterates so the interface stays binary-compatible.
     */
    default int bulkTombstoneByTree(UUID treeId, java.time.Instant at) {
        int n = 0;
        for (Relationship r : listByTree(treeId, false)) {
            r.tombstone(r.version(), at);
            update(r);
            n++;
        }
        return n;
    }
}