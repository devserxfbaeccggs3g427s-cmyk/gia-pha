package com.familya.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một người dùng mới được tạo.
 *
 * <p>Sự kiện này mang theo email đã chuẩn hóa và cờ yêu cầu xác minh
 * giúp các service hạ tầng (notification, analytics,…) quyết định
 * hành động phù hợp: gửi email chào mừng, gửi mã xác minh,…
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class IdentityUserCreated extends IdentityEvent {
    /** UUID người dùng vừa tạo. */
    private final UUID userId;
    /** Email đã được chuẩn hóa (lowercase, trim). */
    private final String normalizedEmail;
    /** Cờ cho biết người dùng có cần xác minh email hay không. */
    private final boolean verificationRequired;
    /** Thời điểm tạo người dùng. */
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện.
     *
     * @param userId              UUID người dùng mới.
     * @param normalizedEmail     email đã chuẩn hóa.
     * @param verificationRequired cờ yêu cầu xác minh.
     * @param occurredAt          thời điểm tạo.
     */
    public IdentityUserCreated(UUID userId, String normalizedEmail, boolean verificationRequired, Instant occurredAt) {
        this.userId = userId;
        this.normalizedEmail = normalizedEmail;
        this.verificationRequired = verificationRequired;
        this.occurredAt = occurredAt;
    }

    /**
     * @return UUID người dùng.
     */
    @Override public UUID userId() { return userId; }

    /**
     * @return tên loại sự kiện {@code "IdentityUserCreated"}.
     */
    @Override public String eventType() { return "IdentityUserCreated"; }

    /**
     * @return phiên bản schema {@code 1}.
     */
    @Override public int eventVersion() { return 1; }

    /**
     * @return thời điểm tạo.
     */
    @Override public Instant occurredAt() { return occurredAt; }

    /**
     * @return email đã chuẩn hóa.
     */
    public String normalizedEmail() { return normalizedEmail; }

    /**
     * @return {@code true} nếu người dùng cần xác minh email.
     */
    public boolean verificationRequired() { return verificationRequired; }
}
