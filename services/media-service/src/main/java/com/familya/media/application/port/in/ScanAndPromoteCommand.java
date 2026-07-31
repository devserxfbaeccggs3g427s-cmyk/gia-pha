package com.familya.media.application.port.in;

import java.util.UUID;

public record ScanAndPromoteCommand(
        UUID mediaId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision) {
}
