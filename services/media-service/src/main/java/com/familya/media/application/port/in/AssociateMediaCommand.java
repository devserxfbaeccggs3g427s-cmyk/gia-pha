package com.familya.media.application.port.in;

import java.util.UUID;

public record AssociateMediaCommand(
        UUID mediaId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        String targetKind,
        UUID targetId) {
}
