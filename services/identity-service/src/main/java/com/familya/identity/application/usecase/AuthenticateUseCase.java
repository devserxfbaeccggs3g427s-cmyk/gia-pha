package com.familya.identity.application.usecase;

import com.familya.identity.application.port.in.AuthenticateCommand;
import com.familya.identity.application.port.out.IdentityEventPublisher;
import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.identity.domain.event.IdentityCredentialsRehashed;
import com.familya.identity.domain.event.IdentityUserLocked;
import com.familya.identity.domain.exception.AccountLockedException;
import com.familya.identity.domain.exception.InvalidCredentialsException;
import com.familya.identity.domain.model.Session;
import com.familya.identity.domain.model.User;
import com.familya.platform.security.PasswordEncoder;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Use case xử lý đăng nhập (xác thực) người dùng.
 *
 * <p>Use case này thực hiện các bước sau:
 * <ol>
 *     <li>Tra cứu người dùng theo email đã chuẩn hóa.</li>
 *     <li>Kiểm tra trạng thái khóa tài khoản.</li>
 *     <li>So sánh mật khẩu với hash lưu trong cơ sở dữ liệu.</li>
 *     <li>Quản lý số lần đăng nhập thất bại và tự động khóa tài khoản
 *         khi vượt ngưỡng.</li>
 *     <li>Rehash mật khẩu nếu cost factor không còn đủ mạnh.</li>
 *     <li>Tạo phiên đăng nhập mới với thời hạn tuyệt đối và thời hạn
 *         idle.</li>
 *     <li>Phát hành các sự kiện domain {@link IdentityUserLocked} và
 *         {@link IdentityCredentialsRehashed} khi cần.</li>
 * </ol>
 *
 * <p>Use case này là ví dụ điển hình của việc kết hợp:
 * <ul>
 *     <li>Optimal locking ở tầng persistence.</li>
 *     <li>Metrics giám sát tỷ lệ thất bại.</li>
 *     <li>Phát hành sự kiện thông qua Outbox.</li>
 * </ul>
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@Service
public class AuthenticateUseCase {

    /** Logger ghi lại các sự kiện bảo mật quan trọng. */
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticateUseCase.class);

    /** Cổng dữ liệu identity. */
    private final IdentityRepository repo;
    /** Bộ mã hóa / xác thực mật khẩu. */
    private final PasswordEncoder encoder;
    /** Bộ phát hành sự kiện. */
    private final IdentityEventPublisher eventPublisher;
    /** Bộ thu thập metric. */
    private final PlatformMetrics metrics;
    /** Đồng hồ (để dễ test với fake clock). */
    private final RegisterUserUseCase.Clock clock;
    /** Số lần đăng nhập thất bại tối đa trước khi khóa tài khoản. */
    private final int failedAttemptsLimit;
    /** Khoảng thời gian khóa tài khoản. */
    private final Duration lockoutWindow;
    /** Thời hạn idle của phiên (rolls forward khi có hoạt động). */
    private final Duration sessionIdle;
    /** Thời hạn tuyệt đối của phiên (kể cả khi có hoạt động). */
    private final Duration sessionAbsolute;

    /**
     * Khởi tạo use case với đầy đủ phụ thuộc và cấu hình.
     *
     * @param repo                cổng dữ liệu identity.
     * @param encoder             bộ mã hóa mật khẩu.
     * @param eventPublisher      bộ phát hành sự kiện.
     * @param metrics             bộ thu thập metric.
     * @param clock               đồng hồ.
     * @param failedAttemptsLimit ngưỡng đăng nhập thất bại (mặc định 5).
     * @param lockoutWindowMinutes thời gian khóa (phút, mặc định 15).
     * @param sessionIdleMinutes  thời hạn idle (phút, mặc định 30).
     * @param sessionAbsoluteHours thời hạn tuyệt đối (giờ, mặc định 12).
     */
    public AuthenticateUseCase(IdentityRepository repo,
                               PasswordEncoder encoder,
                               IdentityEventPublisher eventPublisher,
                               PlatformMetrics metrics,
                               RegisterUserUseCase.Clock clock,
                               @Value("${familya.identity.failed-attempts-limit:5}") int failedAttemptsLimit,
                               @Value("${familya.identity.lockout-window-minutes:15}") long lockoutWindowMinutes,
                               @Value("${familya.identity.session-idle-minutes:30}") long sessionIdleMinutes,
                               @Value("${familya.identity.session-absolute-hours:12}") long sessionAbsoluteHours) {
        this.repo = repo;
        this.encoder = encoder;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.clock = clock;
        this.failedAttemptsLimit = failedAttemptsLimit;
        this.lockoutWindow = Duration.ofMinutes(lockoutWindowMinutes);
        this.sessionIdle = Duration.ofMinutes(sessionIdleMinutes);
        this.sessionAbsolute = Duration.ofHours(sessionAbsoluteHours);
    }

    /**
     * Thực hiện xác thực người dùng.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Tăng metric "mutation accepted" cho hoạt động đăng nhập.</li>
     *     <li>Lấy thời điểm hiện tại từ {@link RegisterUserUseCase.Clock}.</li>
     *     <li>Tra cứu người dùng theo email chuẩn hóa. Nếu không tồn tại
     *         -> ném {@link InvalidCredentialsException} và ghi nhận
     *         metric "credentials.invalid".</li>
     *     <li>Kiểm tra trạng thái khóa: nếu tài khoản đang bị khóa và
     *         chưa hết hạn -> ném {@link AccountLockedException}.</li>
     *     <li>So sánh mật khẩu. Nếu sai:
     *         <ul>
     *             <li>Tăng số lần thất bại. Nếu >= ngưỡng -> khóa tài
     *                 khoản, phát {@link IdentityUserLocked} và ném
     *                 {@link AccountLockedException}.</li>
     *             <li>Nếu chưa đạt ngưỡng -> cập nhật số lần thất bại
     *                 và ném {@link InvalidCredentialsException}.</li>
     *         </ul>
     *     </li>
     *     <li>Nếu mật khẩu đúng:
     *         <ul>
     *             <li>Reset trạng thái khóa / số lần thất bại.</li>
     *             <li>Nếu hash cần rehash -> cập nhật hash mới và phát
     *                 {@link IdentityCredentialsRehashed}.</li>
     *             <li>Lưu thay đổi.</li>
     *             <li>Tạo phiên mới với UUID mới, thời hạn tuyệt đối
     *                 và thời hạn idle tính từ {@code now}.</li>
     *         </ul>
     *     </li>
     * </ol>
     *
     * <p>Annotation {@link Transactional} cấu hình
     * {@code noRollbackFor} cho hai exception nghiệp vụ – mục đích là
     * để những thay đổi (như cập nhật số lần thất bại, khóa tài khoản)
     * vẫn được commit ngay cả khi ném exception.
     *
     * @param cmd command chứa email, mật khẩu, IP và user-agent.
     * @return {@link Session} mới được tạo nếu đăng nhập thành công.
     * @throws InvalidCredentialsException nếu email không tồn tại hoặc mật khẩu sai.
     * @throws AccountLockedException      nếu tài khoản đang bị khóa hoặc vừa bị khóa do vượt ngưỡng.
     */
    @Transactional(noRollbackFor = { InvalidCredentialsException.class, AccountLockedException.class })
    public Session execute(AuthenticateCommand cmd) {
        // Bước 1: ghi nhận metric "mutation accepted" cho mỗi lượt đăng nhập.
        metrics.mutationAcceptedCounter("identity-service", "authenticate").increment();
        // Bước 2: lấy thời điểm hiện tại từ clock (hỗ trợ test với fake clock).
        Instant now = clock.now();
        // Bước 3: tra cứu người dùng theo email chuẩn hóa.
        User user = repo.findByNormalizedEmail(normalize(cmd.email()))
                .orElseThrow(() -> {
                    // Không tồn tại -> đăng nhập sai -> ghi nhận metric thất bại.
                    metrics.mutationFailed("identity-service", "authenticate", "credentials.invalid");
                    return new InvalidCredentialsException();
                });
        // Bước 4: nếu tài khoản đang bị khóa và chưa hết hạn -> từ chối.
        if (user.lockout().locked() && user.lockout().lockedUntil().isAfter(now)) {
            throw new AccountLockedException("Account is locked until " + user.lockout().lockedUntil());
        }
        // Bước 5: so sánh mật khẩu.
        if (!encoder.matches(cmd.password(), user.bcryptHash())) {
            // Tăng số lần thất bại.
            int attempts = user.failedAttempts() + 1;
            if (attempts >= failedAttemptsLimit) {
                // Đạt ngưỡng -> khóa tài khoản và phát sự kiện.
                Instant lockUntil = now.plus(lockoutWindow);
                User updated = user.withLockout(new User.LockoutState(true, lockUntil), attempts, now);
                repo.update(updated);
                eventPublisher.publish(new IdentityUserLocked(updated.id(), lockUntil, now));
                LOG.warn("User locked id={} until={}", updated.id(), lockUntil);
                metrics.mutationFailed("identity-service", "authenticate", "credentials.locked");
                throw new AccountLockedException("Account is locked until " + lockUntil);
            }
            // Chưa đạt ngưỡng -> chỉ cập nhật số lần thất bại.
            User updated = user.withLockout(user.lockout(), attempts, now);
            repo.update(updated);
            metrics.mutationFailed("identity-service", "authenticate", "credentials.invalid");
            throw new InvalidCredentialsException();
        }
        // Bước 6: mật khẩu đúng - reset trạng thái khóa và số lần thất bại.
        User rehash = user.withLockout(new User.LockoutState(false, null), 0, now);
        // Bước 7: nếu hash cần được nâng cấp (cost factor không đủ mạnh) -> rehash.
        if (encoder.needsRehash(user.bcryptHash())) {
            rehash = rehash.withBcrypt(encoder.hash(cmd.password()), now);
            // Phát sự kiện để các hệ thống khác (audit, notification) biết.
            eventPublisher.publish(new IdentityCredentialsRehashed(rehash.id(), now));
        }
        // Bước 8: lưu thay đổi (reset lockout, có thể đã cập nhật hash).
        repo.update(rehash);
        // Bước 9: tạo phiên đăng nhập mới.
        Session session = new Session(
                UUID.randomUUID(),
                rehash.id(),
                now,
                now.plus(sessionAbsolute),
                now.plus(sessionIdle),
                cmd.userAgent(),
                ipHash(cmd.ipAddress()));
        repo.insertSession(session);
        return session;
    }

    /**
     * Chuẩn hóa email: cắt khoảng trắng và chuyển về chữ thường.
     *
     * @param email email đầu vào.
     * @return email đã chuẩn hóa.
     */
    private static String normalize(String email) { return email.trim().toLowerCase(); }

    /**
     * Băm IP để không lưu trữ địa chỉ thô (bảo mật / tuân thủ GDPR).
     *
     * <p>Đây là hàm băm đơn giản dựa trên {@code hashCode()} của Java –
     * chỉ dùng để có một định danh ổn định cho cùng một IP, không có
     * mục đích mật mã. Với yêu cầu bảo mật cao hơn có thể thay bằng
     * HMAC-SHA256.
     *
     * @param ip địa chỉ IP.
     * @return chuỗi hex đại diện cho IP, hoặc {@code null} nếu IP null.
     */
    private static String ipHash(String ip) { return ip == null ? null : Integer.toHexString(ip.hashCode()); }
}
