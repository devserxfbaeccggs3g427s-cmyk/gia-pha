package com.familya.sharing.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record CreateShareLinkCommand(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String scope,
        UUID targetId,
        String role,
        Instant expiresAt) {
}
