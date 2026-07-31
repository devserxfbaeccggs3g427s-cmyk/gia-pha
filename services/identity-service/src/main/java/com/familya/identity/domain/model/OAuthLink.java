package com.familya.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate value object đại diện cho liên kết giữa người dùng và một
 * tài khoản OAuth bên ngoài (Google, Facebook, GitHub,…).
 *
 * <p>Mỗi liên kết xác định duy nhất bằng:
 * <ul>
 *     <li>{@code id} – UUID nội bộ của bản ghi liên kết.</li>
 *     <li>{@code userId} – UUID người dùng trong hệ thống.</li>
 *     <li>{@code provider} – tên nhà cung cấp OAuth (ví dụ: "google").</li>
 *     <li>{@code providerSubject} – mã định danh (subject) do nhà cung
 *         cấp phát hành.</li>
 *     <li>{@code normalizedEmail} – email đã chuẩn hóa tại thời điểm
 *         liên kết (có thể null).</li>
 * </ul>
 *
 * <p>Đây là aggregate bất biến – mọi thay đổi phải tạo thể hiện mới.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class OAuthLink {
    /** UUID nội bộ của bản ghi liên kết. */
    private final UUID id;
    /** UUID người dùng. */
    private final UUID userId;
    /** Tên nhà cung cấp OAuth. */
    private final String provider;
    /** Subject ID do nhà cung cấp phát hành. */
    private final String providerSubject;
    /** Email đã chuẩn hóa tại thời điểm liên kết (có thể null). */
    private final String normalizedEmail;

    /**
     * Khởi tạo liên kết OAuth.
     *
     * @param id              UUID của bản ghi (bắt buộc).
     * @param userId          UUID người dùng (bắt buộc).
     * @param provider        tên nhà cung cấp (bắt buộc).
     * @param providerSubject mã subject (bắt buộc).
     * @param normalizedEmail email chuẩn hóa (có thể null).
     * @throws NullPointerException nếu một trong các tham số bắt buộc là null.
     */
    public OAuthLink(UUID id, UUID userId, String provider, String providerSubject, String normalizedEmail) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.provider = Objects.requireNonNull(provider);
        this.providerSubject = Objects.requireNonNull(providerSubject);
        this.normalizedEmail = normalizedEmail;
    }

    /**
     * @return UUID của bản ghi liên kết.
     */
    public UUID id() { return id; }

    /**
     * @return UUID người dùng.
     */
    public UUID userId() { return userId; }

    /**
     * @return tên nhà cung cấp OAuth.
     */
    public String provider() { return provider; }

    /**
     * @return mã subject do nhà cung cấp phát hành.
     */
    public String providerSubject() { return providerSubject; }

    /**
     * @return email chuẩn hóa hoặc {@code null} nếu không có.
     */
    public String normalizedEmail() { return normalizedEmail; }
}
