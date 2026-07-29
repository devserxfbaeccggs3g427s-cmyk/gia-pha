package com.familya.treeaccess.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Membership row. The {@code revoked_at} field doubles as a soft-delete
 * marker; an un-revoked row holds the active role. {@link #effectiveRole()}
 * is the role used by authorization projections.
 */
public final class TreeMembership {

    private final UUID id;
    private final UUID treeId;
    private final UUID userId;
    private Role role;
    private final UUID grantedBy;
    private final Instant grantedAt;
    private Instant revokedAt;
    private UUID revokedBy;
    private String revocationReason;

    public TreeMembership(UUID id, UUID treeId, UUID userId, Role role,
                          UUID grantedBy, Instant grantedAt,
                          Instant revokedAt, UUID revokedBy, String revocationReason) {
        this.id = Objects.requireNonNull(id);
        this.treeId = Objects.requireNonNull(treeId);
        this.userId = Objects.requireNonNull(userId);
        this.role = Objects.requireNonNull(role);
        this.grantedBy = Objects.requireNonNull(grantedBy);
        this.grantedAt = Objects.requireNonNull(grantedAt);
        this.revokedAt = revokedAt;
        this.revokedBy = revokedBy;
        this.revocationReason = revocationReason;
    }

    public UUID id() { return id; }
    public UUID treeId() { return treeId; }
    public UUID userId() { return userId; }
    public Role role() { return role; }
    public UUID grantedBy() { return grantedBy; }
    public Instant grantedAt() { return grantedAt; }
    public Instant revokedAt() { return revokedAt; }
    public UUID revokedBy() { return revokedBy; }
    public String revocationReason() { return revocationReason; }

    public boolean isActive() { return revokedAt == null; }

    /**
     * Effective role. The Tree owner is always ADMIN regardless of the
     * stored role on their membership row (which may have been removed
     * during the cutover but the owner invariant still holds).
     */
    public Role effectiveRole() {
        return isActive() ? role : null;
    }

    public void revoke(UUID revokedBy, String reason) {
        if (revokedAt != null) {
            return;
        }
        this.revokedAt = Instant.now();
        this.revokedBy = Objects.requireNonNull(revokedBy);
        this.revocationReason = reason;
    }

    public enum Role {
        ADMIN, EDITOR, VIEWER;

        public boolean canEdit() { return this == ADMIN || this == EDITOR; }
        public boolean canView() { return this == ADMIN || this == EDITOR || this == VIEWER; }

        /**
         * Approves the legacy role matrix:
         * - ADMIN: full control (grants, deletes, freeze).
         * - EDITOR: create/update/delete members, relationships, events.
         * - VIEWER: read-only.
         */
        public boolean grantsMembership() { return this == ADMIN; }
    }
}