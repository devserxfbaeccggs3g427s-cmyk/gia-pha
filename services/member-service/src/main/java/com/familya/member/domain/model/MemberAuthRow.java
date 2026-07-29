package com.familya.member.domain.model;

import com.familya.platform.projection.ProjectionHeader;
import com.familya.platform.projection.AuthorizationProjection;

import java.util.UUID;

/**
 * Authorization projection row. Member Service consumes
 * {@code tree.memberships.v1} into local rows; this record wraps the
 * row with the SDK's {@link ProjectionHeader} so the
 * {@link AuthorizationProjection} SDK can apply its policy uniformly.
 */
public record MemberAuthRow(UUID treeId, UUID userId, String role,
                             long revision, long epoch,
                             java.time.Instant grantedAt,
                             boolean revoked,
                             String sourceEventId,
                             java.time.Instant lastUpdatedAt)
        implements AuthorizationProjection.ProjectionRow {

    public ProjectionHeader header() {
        return new ProjectionHeader(treeId, revision, epoch, lastUpdatedAt,
                lastUpdatedAt == null ? 0L : lastUpdatedAt.toEpochMilli(),
                sourceEventId);
    }

    public boolean isRevoked() { return revoked || role == null; }

    public boolean canEdit() {
        return role != null && (role.equals("ADMIN") || role.equals("EDITOR"));
    }

    public boolean canView() {
        return role != null && (role.equals("ADMIN") || role.equals("EDITOR") || role.equals("VIEWER"));
    }
}