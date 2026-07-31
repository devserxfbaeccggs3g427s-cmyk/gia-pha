package com.familya.media.application.port.in;

import java.util.UUID;

public record RestoreMemberMediaReferencesCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId) {
}