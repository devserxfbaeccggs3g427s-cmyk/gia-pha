package com.familya.media.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record CreateUploadIntentCommand(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String mimeType,
        long byteSize,
        String originalFilename,
        String sha256,
        Instant clientStartedAt) {
}
