package com.familya.relationship.application.port.in;

import com.familya.relationship.domain.model.Relationship;

import java.util.UUID;

public record CreateRelationshipCommand(
        UUID treeId,
        UUID actingUser,
        Relationship.Kind kind,
        UUID fromMemberId,
        UUID toMemberId,
        String metadataJson,
        long expectedTreeRevision
) { }