package com.familya.relationship.application.port.out;

import com.familya.relationship.domain.model.Relationship;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelationshipRepository {

    void insert(Relationship rel);

    Optional<Relationship> findById(UUID id);

    List<Relationship> listByTree(UUID treeId, boolean includeTombstoned);

    /**
     * Returns the highest committed command sequence for the tree
     * (used by the per-tree serializer). Inside a {@code SELECT …
     * FOR UPDATE} so concurrent transactions serialise.
     */
    long nextCommandSeq(UUID treeId);

    void appendCommandLog(UUID treeId, long commandSeq, String commandType,
                          UUID actorUserId, String payloadHash, java.time.Instant committedAt);

    void update(Relationship rel);

    boolean existsEdge(UUID treeId, Relationship.Kind kind, UUID from, UUID to);
}