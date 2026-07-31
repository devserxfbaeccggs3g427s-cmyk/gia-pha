package com.familya.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate đại diện cho một phiên đăng nhập của người dùng.
 *
 * <p>Một phiên chứa:
 * <ul>
 *     <li>{@code id} – UUID định danh phiên (dùng trong cookie).</li>
 *     <li>{@code userId} – UUID người dùng sở hữu phiên.</li>
 *     <li>{@code createdAt} – thời điểm tạo phiên.</li>
 *     <li>{@code absoluteExpiresAt} – thời điểm hết hạn tuyệt đối
 *         (kể cả khi người dùng vẫn đang hoạt động).</li>
 *     <li>{@code idleExpiresAt} – thời điểm hết hạn nếu không có hoạt
 *         động nào (cuộn về phía trước khi có hoạt động).</li>
 *     <li>{@code userAgent} – chuỗi User-Agent tại thời điểm đăng nhập.</li>
 *     <li>{@code ipHash} – băm của địa chỉ IP (phục vụ audit).</li>
 *     <li>{@code revoked} – cờ thu hồi (mutate được – chỉ thay đổi qua
 *         {@link #revoke()}).</li>
 * </ul>
 *
 * <p>Hầu hết các trường là bất biến; chỉ có trường {@code revoked} có
 * thể thay đổi thông qua {@link #revoke()} để mô phỏng cờ trạng thái
 * một cách an toàn.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class Session {

    /** UUID định danh phiên. */
    private final UUID id;
    /** UUID người dùng sở hữu phiên. */
    private final UUID userId;
    /** Thời điểm tạo phiên. */
    private final Instant createdAt;
    /** Thời điểm hết hạn tuyệt đối. */
    private final Instant absoluteExpiresAt;
    /** Thời điểm hết hạn khi không có hoạt động. */
    private final Instant idleExpiresAt;
    /** Chuỗi User-Agent tại thời điểm đăng nhập. */
    private final String userAgent;
    /** Băm địa chỉ IP. */
    private final String ipHash;
    /** Cờ cho biết phiên đã bị thu hồi hay chưa. */
    private boolean revoked;

    /**
     * Khởi tạo phiên.
     *
     * @param id                UUID phiên (bắt buộc).
     * @param userId            UUID người dùng (bắt buộc).
     * @param createdAt         thời điểm tạo (bắt buộc).
     * @param absoluteExpiresAt thời điểm hết hạn tuyệt đối (bắt buộc).
     * @param idleExpiresAt     thời điểm hết hạn idle (bắt buộc).
     * @param userAgent         chuỗi User-Agent (có thể null).
     * @param ipHash            băm IP (có thể null).
     * @throws NullPointerException nếu một trong các tham số bắt buộc là null.
     */
    public Session(UUID id,
                   UUID userId,
                   Instant createdAt,
                   Instant absoluteExpiresAt,
                   Instant idleExpiresAt,
                   String userAgent,
                   String ipHash) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.absoluteExpiresAt = Objects.requireNonNull(absoluteExpiresAt);
        this.idleExpiresAt = Objects.requireNonNull(idleExpiresAt);
        this.userAgent = userAgent;
        this.ipHash = ipHash;
    }

    /**
     * @return UUID phiên.
     */
    public UUID id() { return id; }

    /**
     * @return UUID người dùng sở hữu phiên.
     */
    public UUID userId() { return userId; }

    /**
     * @return thời điểm tạo phiên.
     */
    public Instant createdAt() { return createdAt; }

    /**
     * @return thời điểm hết hạn tuyệt đối.
     */
    public Instant absoluteExpiresAt() { return absoluteExpiresAt; }

    /**
     * @return thời điểm hết hạn idle.
     */
    public Instant idleExpiresAt() { return idleExpiresAt; }

    /**
     * @return chuỗi User-Agent hoặc {@code null}.
     */
    public String userAgent() { return userAgent; }

    /**
     * @return băm địa chỉ IP hoặc {@code null}.
     */
    public String ipHash() { return ipHash; }

    /**
     * @return {@code true} nếu phiên đã bị thu hồi, ngược lại {@code false}.
     */
    public boolean revoked() { return revoked; }

    /**
     * Đánh dấu phiên đã bị thu hồi và trả về chính thể hiện này để
     * hỗ trợ fluent API.
     *
     * <p>Phương thức này đánh dấu cờ {@code revoked} và đồng thời
     * trả về {@code this} để hỗ trợ cú pháp như:
     * <pre>{@code repo.updateSession(s.revoke());}</pre>
     *
     * @return chính thể hiện {@code Session} sau khi đánh dấu thu hồi.
     */
    public Session revoke() { this.revoked = true; return this; }

    /**
     * Kiểm tra phiên có đang hoạt động tại thời điểm {@code now} hay không.
     *
     * <p>Phiên hoạt động khi:
     * <ul>
     *     <li>Chưa bị thu hồi.</li>
     *     <li>Chưa vượt quá thời hạn tuyệt đối.</li>
     *     <li>Chưa vượt quá thời hạn idle.</li>
     * </ul>
     *
     * @param now thời điểm kiểm tra.
     * @return {@code true} nếu phiên còn hoạt động, {@code false} nếu ngược lại.
     */
    public boolean isActive(Instant now) {
        return !revoked && now.isBefore(absoluteExpiresAt) && now.isBefore(idleExpiresAt);
    }
}
