package com.familya.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi người dùng hoàn tất xác minh email.
 *
 * <p>Sự kiện này cho phép các hệ thống khác (welcome flow, marketing,
 * …) phản ứng với việc người dùng đã hoàn tất onboarding.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class IdentityUserVerified extends IdentityEvent {
    /** UUID người dùng đã xác minh. */
    private final UUID userId;
    /** Thời điểm xác minh. */
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện.
     *
     * @param userId     UUID người dùng đã xác minh.
     * @param occurredAt thời điểm xác minh.
     */
    public IdentityUserVerified(UUID userId, Instant occurredAt) {
        this.userId = userId;
        this.occurredAt = occurredAt;
    }

    /**
     * @return UUID người dùng.
     */
    @Override public UUID userId() { return userId; }

    /**
     * @return tên loại sự kiện {@code "IdentityUserVerified"}.
     */
    @Override public String eventType() { return "IdentityUserVerified"; }

    /**
     * @return phiên bản schema {@code 1}.
     */
    @Override public int eventVersion() { return 1; }

    /**
     * @return thời điểm xác minh.
     */
    @Override public Instant occurredAt() { return occurredAt; }
}
