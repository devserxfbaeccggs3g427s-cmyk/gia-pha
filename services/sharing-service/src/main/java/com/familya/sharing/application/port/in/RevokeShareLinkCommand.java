package com.familya.sharing.application.port.in;

import java.util.UUID;

public record RevokeShareLinkCommand(
        UUID shareId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        String reason) {
}
