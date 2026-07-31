package com.familya.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate value object đại diện cho một token xác minh email.
 *
 * <p>Token được dùng để xác nhận địa chỉ email người dùng thuộc về họ.
 * Mỗi token có:
 * <ul>
 *     <li>UUID người dùng mà token thuộc về.</li>
 *     <li>Chuỗi token (thường là chuỗi ngẫu nhiên được gửi qua email).</li>
 *     <li>Thời điểm hết hạn.</li>
 *     <li>Thời điểm đã sử dụng (null nếu chưa sử dụng).</li>
 * </ul>
 *
 * <p>Lớp này là bất biến – mọi thay đổi (như đánh dấu đã sử dụng) tạo
 * ra thể hiện mới thông qua {@link #consume(Instant)} để tôn trọng
 * các nguyên tắc Domain-Driven Design.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class EmailVerificationToken {

    /** UUID người dùng mà token thuộc về. */
    private final UUID userId;
    /** Chuỗi token. */
    private final String token;
    /** Thời điểm hết hạn. */
    private final Instant expiresAt;
    /** Thời điểm đã sử dụng (null nếu chưa sử dụng). */
    private final Instant consumedAt;

    /**
     * Khởi tạo token.
     *
     * @param userId     UUID người dùng (bắt buộc).
     * @param token      chuỗi token (bắt buộc).
     * @param expiresAt  thời điểm hết hạn (bắt buộc).
     * @param consumedAt thời điểm đã sử dụng (có thể null).
     * @throws NullPointerException nếu userId, token hoặc expiresAt là null.
     */
    public EmailVerificationToken(UUID userId, String token, Instant expiresAt, Instant consumedAt) {
        this.userId = Objects.requireNonNull(userId);
        this.token = Objects.requireNonNull(token);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.consumedAt = consumedAt;
    }

    /**
     * @return UUID người dùng sở hữu token.
     */
    public UUID userId() { return userId; }

    /**
     * @return chuỗi token.
     */
    public String token() { return token; }

    /**
     * @return thời điểm hết hạn.
     */
    public Instant expiresAt() { return expiresAt; }

    /**
     * @return thời điểm đã sử dụng, hoặc {@code null} nếu chưa sử dụng.
     */
    public Instant consumedAt() { return consumedAt; }

    /**
     * Kiểm tra token còn khả dụng tại thời điểm {@code now} hay không.
     *
     * <p>Token còn khả dụng khi:
     * <ul>
     *     <li>Chưa được sử dụng ({@code consumedAt == null}).</li>
     *     <li>Chưa hết hạn ({@code expiresAt > now}).</li>
     * </ul>
     *
     * @param now thời điểm kiểm tra.
     * @return {@code true} nếu token còn khả dụng, {@code false} nếu ngược lại.
     */
    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    /**
     * Tạo bản sao token đã được đánh dấu sử dụng tại thời điểm {@code now}.
     *
     * <p>Phương thức này thực hiện kiểm tra {@link #isUsable(Instant)}
     * trước khi tạo bản sao để đảm bảo không thể "tiêu thụ" một token
     * đã hết hạn hoặc đã được sử dụng trước đó.
     *
     * @param now thời điểm tiêu thụ.
     * @return bản sao token với {@code consumedAt = now}.
     * @throws IllegalStateException nếu token không còn khả dụng.
     */
    public EmailVerificationToken consume(Instant now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("Token is not usable");
        }
        return new EmailVerificationToken(userId, token, expiresAt, now);
    }
}
