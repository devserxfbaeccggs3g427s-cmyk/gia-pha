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

    /**
     * @param id                 mã membership
     * @param treeId             mã cây
     * @param userId             UUID người dùng
     * @param role               vai trò
     * @param grantedBy          UUID người cấp
     * @param grantedAt          thời điểm cấp
     * @param revokedAt          thời điểm thu hồi hoặc {@code null}
     * @param revokedBy          UUID người thu hồi hoặc {@code null}
     * @param revocationReason   lý do thu hồi hoặc {@code null}
     */
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

    /**
     * @return mã membership
     */
    public UUID id() { return id; }
    /**
     * @return mã cây
     */
    public UUID treeId() { return treeId; }
    /**
     * @return UUID người dùng
     */
    public UUID userId() { return userId; }
    /**
     * @return vai trò (có thể đã được đổi qua lịch sử grant/revoke)
     */
    public Role role() { return role; }
    /**
     * @return UUID người đã cấp quyền
     */
    public UUID grantedBy() { return grantedBy; }
    /**
     * @return thời điểm cấp quyền
     */
    public Instant grantedAt() { return grantedAt; }
    /**
     * @return thời điểm thu hồi hoặc {@code null} nếu đang hoạt động
     */
    public Instant revokedAt() { return revokedAt; }
    /**
     * @return UUID người đã thu hồi hoặc {@code null}
     */
    public UUID revokedBy() { return revokedBy; }
    /**
     * @return lý do thu hồi hoặc {@code null}
     */
    public String revocationReason() { return revocationReason; }

    /**
     * @return {@code true} nếu membership đang hoạt động (chưa bị thu hồi)
     */
    public boolean isActive() { return revokedAt == null; }

    /**
     * Effective role. The Tree owner is always ADMIN regardless of the
     * stored role on their membership row (which may have been removed
     * during the cutover but the owner invariant still holds).
     */
    public Role effectiveRole() {
        return isActive() ? role : null;
    }

    /**
     * Thu hồi membership. Idempotent nếu đã thu hồi trước đó.
     *
     * @param revokedBy UUID người thu hồi
     * @param reason    lý do thu hồi
     */
    public void revoke(UUID revokedBy, String reason) {
        if (revokedAt != null) {
            return;
        }
        this.revokedAt = Instant.now();
        this.revokedBy = Objects.requireNonNull(revokedBy);
        this.revocationReason = reason;
    }

    /**
     * Vai trò thành viên trong ma trận phân quyền kế thừa:
     * <ul>
     *   <li>ADMIN — toàn quyền (cấp quyền, xóa, đóng băng).</li>
     *   <li>EDITOR — tạo/cập nhật/xóa thành viên, quan hệ, sự kiện.</li>
     *   <li>VIEWER — chỉ đọc.</li>
     * </ul>
     */
    public enum Role {
        ADMIN, EDITOR, VIEWER;

        /**
         * @return {@code true} nếu vai trò có quyền edit
         */
        public boolean canEdit() { return this == ADMIN || this == EDITOR; }
        /**
         * @return {@code true} nếu vai trò có quyền xem
         */
        public boolean canView() { return this == ADMIN || this == EDITOR || this == VIEWER; }

        /**
         * @return {@code true} nếu vai trò được phép cấp quyền (chỉ ADMIN)
         */
        public boolean grantsMembership() { return this == ADMIN; }
    }
}