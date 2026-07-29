package com.familya.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity aggregate root. Owns credentials, OAuth linkage, lockout,
 * and verification state. Persistence is per ADR-002 (database per
 * service); cross-service references use the opaque {@link #id}.
 */
public final class User {

    private final UUID id;
    private final String normalizedEmail;
    private final String bcryptHash;
    private final VerificationState verification;
    private final LockoutState lockout;
    private final int failedAttempts;
    private final Instant createdAt;
    private final long version;

    public User(UUID id,
                String normalizedEmail,
                String bcryptHash,
                VerificationState verification,
                LockoutState lockout,
                int failedAttempts,
                Instant createdAt,
                long version) {
        this.id = Objects.requireNonNull(id);
        this.normalizedEmail = Objects.requireNonNull(normalizedEmail);
        this.bcryptHash = Objects.requireNonNull(bcryptHash);
        this.verification = Objects.requireNonNull(verification);
        this.lockout = Objects.requireNonNull(lockout);
        this.failedAttempts = failedAttempts;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = version;
    }

    public UUID id() { return id; }
    public String normalizedEmail() { return normalizedEmail; }
    public String bcryptHash() { return bcryptHash; }
    public VerificationState verification() { return verification; }
    public LockoutState lockout() { return lockout; }
    public int failedAttempts() { return failedAttempts; }
    public Instant createdAt() { return createdAt; }
    public long version() { return version; }

    public User withBcrypt(String newHash, Instant now) {
        return new User(id, normalizedEmail, newHash, verification, lockout, failedAttempts, createdAt, version + 1);
    }

    public User withLockout(LockoutState newLockout, int newFailed, Instant now) {
        return new User(id, normalizedEmail, bcryptHash, verification, newLockout, newFailed, createdAt, version + 1);
    }

    public User verified(Instant now) {
        return new User(id, normalizedEmail, bcryptHash, VerificationState.VERIFIED, lockout, 0, createdAt, version + 1);
    }

    public enum VerificationState { UNVERIFIED, PENDING, VERIFIED }
    public record LockoutState(boolean locked, Instant lockedUntil) { }
}
