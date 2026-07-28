package com.familya.relationship.application.port.in;

import com.familya.relationship.domain.model.Relationship;

import java.util.List;
import java.util.UUID;

public record LoadRelationshipManifestCommand(
        UUID treeId,
        List<RelationshipLine> relationships,
        boolean replaySafe
) {
    public record RelationshipLine(
            UUID relationshipId,
            Relationship.Kind kind,
            UUID fromMemberId,
            UUID toMemberId,
            String metadataJson,
            long revision,
            java.time.Instant createdAt
    ) { }
}