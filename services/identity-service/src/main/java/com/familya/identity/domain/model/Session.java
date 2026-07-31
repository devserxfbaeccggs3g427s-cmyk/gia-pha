package com.familya.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Session {

    private final UUID id;
    private final UUID userId;
    private final Instant createdAt;
    private final Instant absoluteExpiresAt;
    private final Instant idleExpiresAt;
    private final String userAgent;
    private final String ipHash;
    private boolean revoked;

    public Session(UUID id,
                   UUID userId,
                   Instant createdAt,
                   Instant absoluteExpiresAt,
                   Instant idleExpiresAt,
                   String userAgent,
                   String ipHash) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.absoluteExpiresAt = Objects.requireNonNull(absoluteExpiresAt);
        this.idleExpiresAt = Objects.requireNonNull(idleExpiresAt);
        this.userAgent = userAgent;
        this.ipHash = ipHash;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public Instant createdAt() { return createdAt; }
    public Instant absoluteExpiresAt() { return absoluteExpiresAt; }
    public Instant idleExpiresAt() { return idleExpiresAt; }
    public String userAgent() { return userAgent; }
    public String ipHash() { return ipHash; }
    public boolean revoked() { return revoked; }

    public Session revoke() { this.revoked = true; return this; }

    public boolean isActive(Instant now) {
        return !revoked && now.isBefore(absoluteExpiresAt) && now.isBefore(idleExpiresAt);
    }
}
