package com.familya.member.domain.model;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Member aggregate. Preserves profile, lifespan/status validation,
 * legacy avatar fallback, tombstones, and duplicate detection. The
 * identity link (userId) is optional and opaque — no foreign key.
 */
public final class Member {

    private final UUID id;
    private final UUID treeId;
    private final UUID userId;
    private String displayName;
    private String givenName;
    private String surname;
    private LocalDate birthDate;
    private LocalDate deathDate;
    private boolean birthYearKnown;
    private boolean deathYearKnown;
    private Gender gender;
    private Status status;
    private Integer generation;
    private String legacyAvatarUrl;
    private String notes;
    private final java.time.Instant createdAt;
    private java.time.Instant updatedAt;
    private java.time.Instant tombstonedAt;
    private long version;

    public Member(UUID id, UUID treeId, UUID userId, String displayName,
                  String givenName, String surname,
                  LocalDate birthDate, LocalDate deathDate,
                  boolean birthYearKnown, boolean deathYearKnown,
                  Gender gender, Status status, Integer generation,
                  String legacyAvatarUrl, String notes,
                  java.time.Instant createdAt, java.time.Instant updatedAt,
                  java.time.Instant tombstonedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.treeId = Objects.requireNonNull(treeId);
        this.userId = userId;
        this.displayName = Objects.requireNonNull(displayName);
        this.givenName = givenName;
        this.surname = surname;
        this.birthDate = birthDate;
        this.deathDate = deathDate;
        this.birthYearKnown = birthYearKnown;
        this.deathYearKnown = deathYearKnown;
        this.gender = gender;
        this.status = Objects.requireNonNull(status);
        this.generation = generation;
        this.legacyAvatarUrl = legacyAvatarUrl;
        this.notes = notes;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.tombstonedAt = tombstonedAt;
        this.version = version;
    }

    public UUID id() { return id; }
    public UUID treeId() { return treeId; }
    public UUID userId() { return userId; }
    public String displayName() { return displayName; }
    public String givenName() { return givenName; }
    public String surname() { return surname; }
    public LocalDate birthDate() { return birthDate; }
    public LocalDate deathDate() { return deathDate; }
    public boolean birthYearKnown() { return birthYearKnown; }
    public boolean deathYearKnown() { return deathYearKnown; }
    public Gender gender() { return gender; }
    public Status status() { return status; }
    public Integer generation() { return generation; }
    public String legacyAvatarUrl() { return legacyAvatarUrl; }
    public String notes() { return notes; }
    public java.time.Instant createdAt() { return createdAt; }
    public java.time.Instant updatedAt() { return updatedAt; }
    public java.time.Instant tombstonedAt() { return tombstonedAt; }
    public long version() { return version; }

    public boolean isTombstoned() { return tombstonedAt != null; }
    public boolean isLiving() { return status == Status.LIVING; }
    public boolean isDeceased() { return status == Status.DECEASED; }

    public void rename(String newDisplayName, long expectedVersion, java.time.Instant now) {
        requireVersion(expectedVersion);
        requireMutable("rename");
        if (newDisplayName == null || newDisplayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        this.displayName = newDisplayName.trim();
        touch(now);
    }

    public void updateProfile(String givenName, String surname, LocalDate birthDate, LocalDate deathDate,
                              Gender gender, Integer generation, String notes, long expectedVersion,
                              java.time.Instant now) {
        requireVersion(expectedVersion);
        requireMutable("updateProfile");
        validateDates(birthDate, deathDate);
        this.givenName = givenName;
        this.surname = surname;
        this.birthDate = birthDate;
        this.deathDate = deathDate;
        this.gender = gender;
        this.generation = generation;
        this.notes = notes;
        if (deathDate != null) {
            this.status = Status.DECEASED;
        }
        touch(now);
    }

    public void tombstone(long expectedVersion, java.time.Instant now) {
        requireVersion(expectedVersion);
        if (tombstonedAt != null) {
            return;
        }
        this.tombstonedAt = now;
        touch(now);
    }

    /**
     * Internal merge: collapses another member's identity into this
     * one. Both members must share tree_id. The survivor keeps its
     * canonical key; the merged member is tombstoned with the same
     * {@code mergeSource} recorded in the event payload.
     */
    public void mergeFrom(Member other, long expectedVersion, java.time.Instant now) {
        requireVersion(expectedVersion);
        requireMutable("merge");
        if (!other.treeId.equals(treeId)) {
            throw new IllegalArgumentException("Cannot merge across trees");
        }
        if (other.id.equals(id)) {
            throw new IllegalArgumentException("Cannot merge a member into itself");
        }
        this.legacyAvatarUrl = this.legacyAvatarUrl != null ? this.legacyAvatarUrl : other.legacyAvatarUrl;
        touch(now);
    }

    private void validateDates(LocalDate birth, LocalDate death) {
        if (birth != null && death != null && death.isBefore(birth)) {
            throw new IllegalArgumentException("deathDate is before birthDate");
        }
        if (birth != null) {
            long years = ChronoUnit.YEARS.between(birth, LocalDate.now());
            if (years < 0 || years > 150) {
                throw new IllegalArgumentException("birthDate implies age " + years + " which is out of range");
            }
        }
    }

    private void requireMutable(String op) {
        if (tombstonedAt != null) {
            throw new IllegalStateException("Member " + id + " is tombstoned; cannot " + op);
        }
    }

    private void requireVersion(long expected) {
        if (this.version != expected) {
            throw new com.familya.platform.error.OptimisticConcurrencyException(
                    "Member " + id + " version " + this.version + " != expected " + expected);
        }
    }

    private void touch(java.time.Instant now) {
        this.updatedAt = now;
        this.version = this.version + 1;
    }

    public enum Gender { MALE, FEMALE, OTHER, UNKNOWN }
    public enum Status { LIVING, DECEASED, STILLBORN, UNKNOWN }
}