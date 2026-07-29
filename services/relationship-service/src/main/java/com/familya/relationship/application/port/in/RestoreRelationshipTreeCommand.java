package com.familya.relationship.application.port.in;

import java.util.UUID;

public record RestoreRelationshipTreeCommand(
        UUID operationId,
        UUID treeId) {
}