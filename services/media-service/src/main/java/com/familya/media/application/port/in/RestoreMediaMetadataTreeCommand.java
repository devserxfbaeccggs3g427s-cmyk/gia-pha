package com.familya.media.application.port.in;

import java.util.UUID;

public record RestoreMediaMetadataTreeCommand(
        UUID operationId,
        UUID treeId) {
}