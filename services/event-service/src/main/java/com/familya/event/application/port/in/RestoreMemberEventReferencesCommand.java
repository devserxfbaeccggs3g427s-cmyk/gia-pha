package com.familya.event.application.port.in;

import java.util.UUID;

public record RestoreMemberEventReferencesCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId) {
}