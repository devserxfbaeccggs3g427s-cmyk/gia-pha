package com.familya.media.application.port.in;

import java.util.UUID;

public record VerifyUploadedCommand(
        UUID mediaId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        String sha256,
        long byteSize) {
}
