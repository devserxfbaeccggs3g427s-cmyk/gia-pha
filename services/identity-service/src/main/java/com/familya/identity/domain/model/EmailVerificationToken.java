package com.familya.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class EmailVerificationToken {

    private final UUID userId;
    private final String token;
    private final Instant expiresAt;
    private final Instant consumedAt;

    public EmailVerificationToken(UUID userId, String token, Instant expiresAt, Instant consumedAt) {
        this.userId = Objects.requireNonNull(userId);
        this.token = Objects.requireNonNull(token);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.consumedAt = consumedAt;
    }

    public UUID userId() { return userId; }
    public String token() { return token; }
    public Instant expiresAt() { return expiresAt; }
    public Instant consumedAt() { return consumedAt; }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public EmailVerificationToken consume(Instant now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("Token is not usable");
        }
        return new EmailVerificationToken(userId, token, expiresAt, now);
    }
}