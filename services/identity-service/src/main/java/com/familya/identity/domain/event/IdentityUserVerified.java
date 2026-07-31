package com.familya.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class IdentityUserVerified extends IdentityEvent {
    private final UUID userId;
    private final Instant occurredAt;

    public IdentityUserVerified(UUID userId, Instant occurredAt) {
        this.userId = userId;
        this.occurredAt = occurredAt;
    }

    @Override public UUID userId() { return userId; }
    @Override public String eventType() { return "IdentityUserVerified"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }
}
