package com.familya.event.domain.model;

import java.time.LocalDate;
import java.util.*;

/**
 * Domain event aggregate. Carries recurrence, deterministic ordering
 * (created_at + sequence), local member/media references (opaque IDs
 * validated against the projections), and tombstone semantics.
 */
public final class DomainEvent {

    private final UUID id;
    private final UUID treeId;
    private String title;
    private String description;
    private Kind kind;
    private LocalDate startDate;
    private LocalDate endDate;
    private RecurrenceRule recurrence;
    private UUID primaryMemberId;
    private List<UUID> additionalMemberIds;
    private List<UUID> mediaRefs;
    private String location;
    private final long revision;
    private final java.time.Instant createdAt;
    private java.time.Instant updatedAt;
    private java.time.Instant tombstonedAt;
    private long version;

    public DomainEvent(UUID id, UUID treeId, String title, String description, Kind kind,
                       LocalDate startDate, LocalDate endDate, RecurrenceRule recurrence,
                       UUID primaryMemberId, List<UUID> additionalMemberIds, List<UUID> mediaRefs,
                       String location, long revision, java.time.Instant createdAt,
                       java.time.Instant updatedAt, java.time.Instant tombstonedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.treeId = Objects.requireNonNull(treeId);
        this.title = Objects.requireNonNull(title);
        this.description = description;
        this.kind = Objects.requireNonNull(kind);
        this.startDate = startDate;
        this.endDate = endDate;
        this.recurrence = recurrence;
        this.primaryMemberId = primaryMemberId;
        this.additionalMemberIds = additionalMemberIds == null ? List.of() : List.copyOf(additionalMemberIds);
        this.mediaRefs = mediaRefs == null ? List.of() : List.copyOf(mediaRefs);
        this.location = location;
        this.revision = revision;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.tombstonedAt = tombstonedAt;
        this.version = version;
    }

    public UUID id() { return id; }
    public UUID treeId() { return treeId; }
    public String title() { return title; }
    public String description() { return description; }
    public Kind kind() { return kind; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public RecurrenceRule recurrence() { return recurrence; }
    public UUID primaryMemberId() { return primaryMemberId; }
    public List<UUID> additionalMemberIds() { return additionalMemberIds; }
    public List<UUID> mediaRefs() { return mediaRefs; }
    public String location() { return location; }
    public long revision() { return revision; }
    public java.time.Instant createdAt() { return createdAt; }
    public java.time.Instant updatedAt() { return updatedAt; }
    public java.time.Instant tombstonedAt() { return tombstonedAt; }
    public long version() { return version; }

    public boolean isTombstoned() { return tombstonedAt != null; }

    public void update(String title, String description, Kind kind,
                       LocalDate startDate, LocalDate endDate, RecurrenceRule recurrence,
                       UUID primaryMemberId, List<UUID> additionalMemberIds, List<UUID> mediaRefs,
                       String location, long expectedVersion, java.time.Instant now) {
        requireVersion(expectedVersion);
        requireMutable("update");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate is before startDate");
        }
        this.title = title;
        this.description = description;
        this.kind = kind;
        this.startDate = startDate;
        this.endDate = endDate;
        this.recurrence = recurrence;
        this.primaryMemberId = primaryMemberId;
        this.additionalMemberIds = additionalMemberIds == null ? List.of() : List.copyOf(additionalMemberIds);
        this.mediaRefs = mediaRefs == null ? List.of() : List.copyOf(mediaRefs);
        this.location = location;
        touch(now);
    }

    public void tombstone(long expectedVersion, java.time.Instant now) {
        requireVersion(expectedVersion);
        if (tombstonedAt != null) return;
        this.tombstonedAt = now;
        touch(now);
    }

    private void requireVersion(long expected) {
        if (this.version != expected) {
            throw new com.familya.platform.error.OptimisticConcurrencyException(
                    "Event " + id + " version " + this.version + " != expected " + expected);
        }
    }

    private void requireMutable(String op) {
        if (tombstonedAt != null) {
            throw new IllegalStateException("Event " + id + " is tombstoned; cannot " + op);
        }
    }

    private void touch(java.time.Instant now) {
        this.updatedAt = now;
        this.version = this.version + 1;
    }

    public enum Kind {
        BIRTH, DEATH, MARRIAGE, ANNIVERSARY, BAPTISM, GRADUATION, CUSTOM, OTHER
    }
}