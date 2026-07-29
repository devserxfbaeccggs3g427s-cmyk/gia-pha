package com.familya.relationship.application.port.in;

import java.util.UUID;

public record RestoreMemberRelationshipsCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId) {
}