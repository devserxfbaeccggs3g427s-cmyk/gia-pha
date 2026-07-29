package com.familya.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

public abstract class IdentityEvent {
    public abstract UUID userId();
    public abstract String eventType();
    public abstract int eventVersion();
    public abstract Instant occurredAt();
}
