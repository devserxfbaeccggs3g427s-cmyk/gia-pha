package com.familya.relationship.application.port.in;

import java.util.UUID;

public record TombstoneRelationshipCommand(UUID relationshipId, UUID actingUser, long expectedVersion, long expectedTreeRevision) { }