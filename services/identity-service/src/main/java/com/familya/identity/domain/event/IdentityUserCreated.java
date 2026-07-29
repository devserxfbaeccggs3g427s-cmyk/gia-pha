package com.familya.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class IdentityUserCreated extends IdentityEvent {
    private final UUID userId;
    private final String normalizedEmail;
    private final boolean verificationRequired;
    private final Instant occurredAt;

    public IdentityUserCreated(UUID userId, String normalizedEmail, boolean verificationRequired, Instant occurredAt) {
        this.userId = userId;
        this.normalizedEmail = normalizedEmail;
        this.verificationRequired = verificationRequired;
        this.occurredAt = occurredAt;
    }

    @Override public UUID userId() { return userId; }
    @Override public String eventType() { return "IdentityUserCreated"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }

    public String normalizedEmail() { return normalizedEmail; }
    public boolean verificationRequired() { return verificationRequired; }
}
