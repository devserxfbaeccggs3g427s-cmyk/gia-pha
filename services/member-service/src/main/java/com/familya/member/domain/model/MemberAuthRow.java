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

    /**
     * Header projection chuẩn cho SDK {@link AuthorizationProjection}.
     * @return {@link ProjectionHeader} với thông tin freshness/revision/epoch
     */
    public ProjectionHeader header() {
        return new ProjectionHeader(treeId, revision, epoch, lastUpdatedAt,
                lastUpdatedAt == null ? 0L : lastUpdatedAt.toEpochMilli(),
                sourceEventId);
    }

    /** Trả về {@code true} nếu vai trò đã bị thu hồi hoặc không có vai trò nào. */
    public boolean isRevoked() { return revoked || role == null; }

    /** Trả về {@code true} nếu người dùng có quyền chỉnh sửa (ADMIN hoặc EDITOR). */
    public boolean canEdit() {
        return role != null && (role.equals("ADMIN") || role.equals("EDITOR"));
    }

    /** Trả về {@code true} nếu người dùng có quyền xem (ADMIN, EDITOR hoặc VIEWER). */
    public boolean canView() {
        return role != null && (role.equals("ADMIN") || role.equals("EDITOR") || role.equals("VIEWER"));
    }
}