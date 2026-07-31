package com.familya.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity aggregate root – đại diện cho người dùng trong hệ thống.
 *
 * <p>Aggregate này sở hữu toàn bộ thông tin liên quan đến danh tính:
 * <ul>
 *     <li>Email đã chuẩn hóa và hash mật khẩu (bcrypt).</li>
 *     <li>Trạng thái xác minh email ({@link VerificationState}).</li>
 *     <li>Trạng thái khóa tài khoản và số lần đăng nhập thất bại
 *         ({@link LockoutState}).</li>
 *     <li>Thời điểm tạo.</li>
 *     <li>Phiên bản {@code version} cho optimistic locking.</li>
 * </ul>
 *
 * <p>Tuân thủ ADR-002 của nền tảng: database per service. Các tham
 * chiếu chéo giữa các service (ví dụ: tree, member) sử dụng
 * {@link #id()} dưới dạng opaque identifier.
 *
 * <p>Aggregate là bất biến – mọi thay đổi được thực hiện thông qua
 * các phương thức factory {@link #withBcrypt}, {@link #withLockout},
 * {@link #verified} để tăng {@code version} và đảm bảo nguyên tắc
 * "no shared mutable state".
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class User {

    /** UUID người dùng. */
    private final UUID id;
    /** Email đã chuẩn hóa (lowercase, trim). */
    private final String normalizedEmail;
    /** Hash mật khẩu (bcrypt). */
    private final String bcryptHash;
    /** Trạng thái xác minh email. */
    private final VerificationState verification;
    /** Trạng thái khóa. */
    private final LockoutState lockout;
    /** Số lần đăng nhập thất bại liên tiếp. */
    private final int failedAttempts;
    /** Thời điểm tạo. */
    private final Instant createdAt;
    /** Phiên bản (optimistic locking). */
    private final long version;

    /**
     * Khởi tạo aggregate.
     *
     * @param id              UUID người dùng (bắt buộc).
     * @param normalizedEmail email chuẩn hóa (bắt buộc).
     * @param bcryptHash      hash mật khẩu (bắt buộc).
     * @param verification    trạng thái xác minh (bắt buộc).
     * @param lockout         trạng thái khóa (bắt buộc).
     * @param failedAttempts  số lần đăng nhập thất bại.
     * @param createdAt       thời điểm tạo (bắt buộc).
     * @param version         phiên bản cho optimistic locking.
     * @throws NullPointerException nếu một trong các tham số bắt buộc là null.
     */
    public User(UUID id,
                String normalizedEmail,
                String bcryptHash,
                VerificationState verification,
                LockoutState lockout,
                int failedAttempts,
                Instant createdAt,
                long version) {
        this.id = Objects.requireNonNull(id);
        this.normalizedEmail = Objects.requireNonNull(normalizedEmail);
        this.bcryptHash = Objects.requireNonNull(bcryptHash);
        this.verification = Objects.requireNonNull(verification);
        this.lockout = Objects.requireNonNull(lockout);
        this.failedAttempts = failedAttempts;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = version;
    }

    /**
     * @return UUID người dùng.
     */
    public UUID id() { return id; }

    /**
     * @return email đã chuẩn hóa.
     */
    public String normalizedEmail() { return normalizedEmail; }

    /**
     * @return hash mật khẩu (bcrypt).
     */
    public String bcryptHash() { return bcryptHash; }

    /**
     * @return trạng thái xác minh email.
     */
    public VerificationState verification() { return verification; }

    /**
     * @return trạng thái khóa tài khoản.
     */
    public LockoutState lockout() { return lockout; }

    /**
     * @return số lần đăng nhập thất bại liên tiếp.
     */
    public int failedAttempts() { return failedAttempts; }

    /**
     * @return thời điểm tạo.
     */
    public Instant createdAt() { return createdAt; }

    /**
     * @return phiên bản (dùng cho optimistic locking).
     */
    public long version() { return version; }

    /**
     * Tạo bản sao với hash mật khẩu mới (dùng khi rehash).
     *
     * <p>Phương thức này tăng {@code version} lên 1 để áp dụng
     * optimistic locking ở tầng persistence.
     *
     * @param newHash hash mật khẩu mới (đã được nâng cấp).
     * @param now     thời điểm thay đổi (giữ chỗ cho việc mở rộng nếu cần).
     * @return bản sao với hash mới và {@code version + 1}.
     */
    public User withBcrypt(String newHash, Instant now) {
        return new User(id, normalizedEmail, newHash, verification, lockout, failedAttempts, createdAt, version + 1);
    }

    /**
     * Tạo bản sao với trạng thái khóa mới và số lần thất bại mới.
     *
     * <p>Phương thức này tăng {@code version} lên 1 để áp dụng
     * optimistic locking.
     *
     * @param newLockout trạng thái khóa mới.
     * @param newFailed  số lần thất bại mới.
     * @param now        thời điểm thay đổi (giữ chỗ).
     * @return bản sao với trạng thái khóa mới và {@code version + 1}.
     */
    public User withLockout(LockoutState newLockout, int newFailed, Instant now) {
        return new User(id, normalizedEmail, bcryptHash, verification, newLockout, newFailed, createdAt, version + 1);
    }

    /**
     * Tạo bản sao đã được xác minh email.
     *
     * <p>Phương thức này:
     * <ul>
     *     <li>Chuyển trạng thái sang {@link VerificationState#VERIFIED}.</li>
     *     <li>Reset số lần đăng nhập thất bại về 0.</li>
     *     <li>Giữ nguyên trạng thái khóa.</li>
     *     <li>Tăng {@code version} lên 1.</li>
     * </ul>
     *
     * @param now thời điểm xác minh (giữ chỗ).
     * @return bản sao với trạng thái đã xác minh.
     */
    public User verified(Instant now) {
        return new User(id, normalizedEmail, bcryptHash, VerificationState.VERIFIED, lockout, 0, createdAt, version + 1);
    }

    /**
     * Enum mô tả trạng thái xác minh email.
     *
     * <ul>
     *     <li>{@link #UNVERIFIED} – chưa bắt đầu xác minh (trạng thái mặc định).</li>
     *     <li>{@link #PENDING} – đã gửi email xác minh, đang chờ người dùng xác nhận.</li>
     *     <li>{@link #VERIFIED} – đã xác minh thành công.</li>
     * </ul>
     */
    public enum VerificationState { UNVERIFIED, PENDING, VERIFIED }

    /**
     * Record mô tả trạng thái khóa tài khoản.
     *
     * @param locked       {@code true} nếu tài khoản đang bị khóa.
     * @param lockedUntil  thời điểm dự kiến mở khóa (có thể null nếu không bị khóa).
     */
    public record LockoutState(boolean locked, Instant lockedUntil) { }
}
