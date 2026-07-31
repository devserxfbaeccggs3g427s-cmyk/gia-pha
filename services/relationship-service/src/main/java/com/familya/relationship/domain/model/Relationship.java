package com.familya.relationship.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Relationship aggregate. The graph node carries the canonical
 * logical key (treeId, kind, fromMember, toMember). Local unique
 * constraint guarantees no duplicate edges per tree.
 *
 * <p>Three kinds are supported:
 * <ul>
 *   <li>{@code PARENT_CHILD}: fromMember = parent, toMember = child.</li>
 *   <li>{@code SPOUSE}: stored once per pair (toMember is the symmetric
 *       partner; the repository inserts the symmetric mirror row).</li>
 *   <li>{@code ADOPTION}: fromMember = adopter, toMember = adopted child.</li>
 * </ul>
 * </p>
 */
public final class Relationship {

    private final UUID id;
    private final UUID treeId;
    private Kind kind;
    private UUID fromMemberId;
    private UUID toMemberId;
    private String metadataJson;
    private final long revision;
    private final java.time.Instant createdAt;
    private java.time.Instant tombstonedAt;
    private long version;

    public Relationship(UUID id, UUID treeId, Kind kind, UUID fromMemberId, UUID toMemberId,
                        String metadataJson, long revision, java.time.Instant createdAt,
                        java.time.Instant tombstonedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.treeId = Objects.requireNonNull(treeId);
        this.kind = Objects.requireNonNull(kind);
        this.fromMemberId = Objects.requireNonNull(fromMemberId);
        this.toMemberId = Objects.requireNonNull(toMemberId);
        this.metadataJson = metadataJson;
        this.revision = revision;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.tombstonedAt = tombstonedAt;
        this.version = version;
    }

    public UUID id() { return id; }
    public UUID treeId() { return treeId; }
    public Kind kind() { return kind; }
    public UUID fromMemberId() { return fromMemberId; }
    public UUID toMemberId() { return toMemberId; }
    public String metadataJson() { return metadataJson; }
    public long revision() { return revision; }
    public java.time.Instant createdAt() { return createdAt; }
    public java.time.Instant tombstonedAt() { return tombstonedAt; }
    public long version() { return version; }

    public boolean isTombstoned() { return tombstonedAt != null; }

    public void tombstone(long expectedVersion, java.time.Instant now) {
        requireVersion(expectedVersion);
        if (tombstonedAt != null) return;
        this.tombstonedAt = now;
        this.version = this.version + 1;
    }

    private void requireVersion(long expected) {
        if (this.version != expected) {
            throw new com.familya.platform.error.OptimisticConcurrencyException(
                    "Relationship " + id + " version " + this.version + " != expected " + expected);
        }
    }

    public enum Kind {
        PARENT_CHILD, SPOUSE, ADOPTION
    }
}