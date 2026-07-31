package com.familya.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi tài khoản người dùng bị khóa do đăng nhập thất
 * bại nhiều lần.
 *
 * <p>Sự kiện này mang theo thời điểm mở khóa dự kiến, giúp các hệ thống
 * khác (notification, support,…) thông báo cho người dùng hoặc cập
 * nhật UI phù hợp.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class IdentityUserLocked extends IdentityEvent {
    /** UUID người dùng bị khóa. */
    private final UUID userId;
    /** Thời điểm dự kiến mở khóa. */
    private final Instant lockedUntil;
    /** Thời điểm phát sinh sự kiện. */
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện.
     *
     * @param userId      UUID người dùng bị khóa.
     * @param lockedUntil thời điểm dự kiến mở khóa.
     * @param occurredAt  thời điểm phát sinh sự kiện.
     */
    public IdentityUserLocked(UUID userId, Instant lockedUntil, Instant occurredAt) {
        this.userId = userId;
        this.lockedUntil = lockedUntil;
        this.occurredAt = occurredAt;
    }

    /**
     * @return UUID người dùng.
     */
    @Override public UUID userId() { return userId; }

    /**
     * @return tên loại sự kiện {@code "IdentityUserLocked"}.
     */
    @Override public String eventType() { return "IdentityUserLocked"; }

    /**
     * @return phiên bản schema {@code 1}.
     */
    @Override public int eventVersion() { return 1; }

    /**
     * @return thời điểm phát sinh sự kiện.
     */
    @Override public Instant occurredAt() { return occurredAt; }

    /**
     * @return thời điểm dự kiến mở khóa tài khoản.
     */
    public Instant lockedUntil() { return lockedUntil; }
}
