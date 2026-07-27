package com.familya.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class IdentityUserLocked extends IdentityEvent {
    private final UUID userId;
    private final Instant lockedUntil;
    private final Instant occurredAt;

    public IdentityUserLocked(UUID userId, Instant lockedUntil, Instant occurredAt) {
        this.userId = userId;
        this.lockedUntil = lockedUntil;
        this.occurredAt = occurredAt;
    }

    @Override public UUID userId() { return userId; }
    @Override public String eventType() { return "IdentityUserLocked"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }

    public Instant lockedUntil() { return lockedUntil; }
}
