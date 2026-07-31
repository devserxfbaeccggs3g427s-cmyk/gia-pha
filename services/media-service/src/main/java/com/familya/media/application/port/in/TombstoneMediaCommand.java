package com.familya.media.application.port.in;

import java.util.UUID;

public record TombstoneMediaCommand(
        UUID mediaId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        java.time.Instant retentionHoldUntil) {
}
