package com.familya.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi mật khẩu của người dùng được băm lại (rehash) với
 * cost factor mạnh hơn.
 *
 * <p>Thường được phát hành ngay sau khi {@code AuthenticateUseCase} phát
 * hiện hash hiện tại không còn đáp ứng chính sách bảo mật. Sự kiện này
 * cho phép các hệ thống audit, notification,… phản ứng phù hợp.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class IdentityCredentialsRehashed extends IdentityEvent {
    /** UUID người dùng. */
    private final UUID userId;
    /** Thời điểm rehash xảy ra. */
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện.
     *
     * @param userId     UUID người dùng vừa được rehash.
     * @param occurredAt thời điểm rehash.
     */
    public IdentityCredentialsRehashed(UUID userId, Instant occurredAt) {
        this.userId = userId;
        this.occurredAt = occurredAt;
    }

    /**
     * @return UUID người dùng.
     */
    @Override public UUID userId() { return userId; }

    /**
     * @return tên loại sự kiện {@code "IdentityCredentialsRehashed"}.
     */
    @Override public String eventType() { return "IdentityCredentialsRehashed"; }

    /**
     * @return phiên bản schema {@code 1}.
     */
    @Override public int eventVersion() { return 1; }

    /**
     * @return thời điểm rehash.
     */
    @Override public Instant occurredAt() { return occurredAt; }
}
