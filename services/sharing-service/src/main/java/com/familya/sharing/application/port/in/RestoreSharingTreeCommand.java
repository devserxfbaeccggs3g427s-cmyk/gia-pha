package com.familya.sharing.application.port.in;

import java.util.UUID;

public record RestoreSharingTreeCommand(
        UUID operationId,
        UUID treeId) {
}