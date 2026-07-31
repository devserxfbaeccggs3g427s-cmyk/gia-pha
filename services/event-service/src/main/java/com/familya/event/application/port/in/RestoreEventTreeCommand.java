package com.familya.event.application.port.in;

import java.util.UUID;

public record RestoreEventTreeCommand(
        UUID operationId,
        UUID treeId) {
}