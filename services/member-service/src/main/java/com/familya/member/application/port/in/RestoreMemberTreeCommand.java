package com.familya.member.application.port.in;

import java.util.UUID;

public record RestoreMemberTreeCommand(
        UUID operationId,
        UUID treeId) {
}