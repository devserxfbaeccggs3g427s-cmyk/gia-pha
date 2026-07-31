package com.familya.identity.application.usecase;

import com.familya.identity.application.port.in.RegisterUserCommand;
import com.familya.identity.application.port.out.IdentityEventPublisher;
import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.identity.application.port.out.RegistrationRateLimiter;
import com.familya.identity.domain.event.IdentityUserCreated;
import com.familya.identity.domain.exception.EmailAlreadyExistsException;
import com.familya.identity.domain.exception.RateLimitExceededException;
import com.familya.identity.domain.model.User;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.security.PasswordEncoder;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Use case đăng ký người dùng mới.
 *
 * <p>Đây là use case cốt lõi của {@code identity-service}, chịu trách
 * nhiệm tạo ra {@link User} mới sau khi:
 * <ul>
 *     <li>Kiểm tra rate limit theo IP.</li>
 *     <li>Đảm bảo email chưa được đăng ký.</li>
 *     <li>Băm mật khẩu.</li>
 *     <li>Lưu người dùng vào cơ sở dữ liệu.</li>
 *     <li>Phát hành sự kiện {@link IdentityUserCreated} để các service
 *         khác (welcome email, analytics,…) phản ứng.</li>
 * </ul>
 *
 * <p>Use case cũng định nghĩa interface lồng nhau {@link Clock} – một
 * abstraction cho {@link Instant#now()} giúp dễ test với thời gian
 * giả lập.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@Service
public class RegisterUserUseCase {

    /** Logger ghi lại các sự kiện đăng ký. */
    private static final Logger LOG = LoggerFactory.getLogger(RegisterUserUseCase.class);

    /** Cổng dữ liệu identity. */
    private final IdentityRepository repo;
    /** Bộ giới hạn đăng ký theo IP. */
    private final RegistrationRateLimiter rateLimiter;
    /** Bộ mã hóa mật khẩu. */
    private final PasswordEncoder encoder;
    /** Bộ ghi outbox (được inject nhưng hiện không sử dụng trực tiếp – sự kiện đi qua eventPublisher). */
    private final OutboxWriter outbox;
    /** Bộ phát hành sự kiện. */
    private final IdentityEventPublisher eventPublisher;
    /** Bộ thu thập metric. */
    private final PlatformMetrics metrics;
    /** Đồng hồ. */
    private final Clock clock;

    /**
     * Khởi tạo use case với các phụ thuộc.
     *
     * @param repo           cổng dữ liệu.
     * @param rateLimiter    bộ rate limit.
     * @param encoder        bộ mã hóa.
     * @param outbox         bộ ghi outbox.
     * @param eventPublisher bộ phát hành sự kiện.
     * @param metrics        bộ thu thập metric.
     * @param clock          đồng hồ.
     */
    public RegisterUserUseCase(IdentityRepository repo,
                               RegistrationRateLimiter rateLimiter,
                               PasswordEncoder encoder,
                               OutboxWriter outbox,
                               IdentityEventPublisher eventPublisher,
                               PlatformMetrics metrics,
                               Clock clock) {
        this.repo = repo;
        this.rateLimiter = rateLimiter;
        this.encoder = encoder;
        this.outbox = outbox;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Thực hiện đăng ký người dùng mới.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Tăng metric "mutation accepted" cho hoạt động đăng ký.</li>
     *     <li>Lấy thời điểm hiện tại từ {@link Clock}.</li>
     *     <li>Kiểm tra rate limit theo IP. Nếu vượt ngưỡng -> ném
     *         {@link RateLimitExceededException}.</li>
     *     <li>Chuẩn hóa email và kiểm tra đã tồn tại hay chưa. Nếu
     *         đã tồn tại -> ném {@link EmailAlreadyExistsException}.</li>
     *     <li>Băm mật khẩu.</li>
     *     <li>Tạo aggregate {@link User}: trạng thái xác minh phụ
     *         thuộc vào {@code verificationRequired}.</li>
     *     <li>Lưu người dùng vào cơ sở dữ liệu (chưa có OAuth link).</li>
     *     <li>Phát hành sự kiện {@link IdentityUserCreated} thông qua
     *         outbox.</li>
     *     <li>Ghi log.</li>
     * </ol>
     *
     * @param cmd command chứa email, mật khẩu, IP và cờ yêu cầu xác minh.
     * @return UUID người dùng vừa được tạo.
     * @throws RateLimitExceededException  nếu IP vượt ngưỡng đăng ký cho phép.
     * @throws EmailAlreadyExistsException nếu email đã tồn tại trong hệ thống.
     */
    @Transactional
    public UUID execute(RegisterUserCommand cmd) {
        // Bước 1: ghi nhận metric "mutation accepted" cho hoạt động đăng ký.
        metrics.mutationAcceptedCounter("identity-service", "registerUser").increment();
        // Bước 2: lấy thời điểm hiện tại.
        Instant now = clock.now();
        // Bước 3: áp dụng rate limit. Nếu vượt ngưỡng -> ném ngoại lệ domain.
        if (!rateLimiter.tryRegister(cmd.ipAddress(), now)) {
            throw new RateLimitExceededException("Registration rate limit exceeded for ip " + cmd.ipAddress());
        }
        // Bước 4: chuẩn hóa email và kiểm tra trùng lặp.
        String normalized = normalize(cmd.email());
        repo.findByNormalizedEmail(normalized).ifPresent(u -> {
            throw new EmailAlreadyExistsException(normalized);
        });
        // Bước 5: băm mật khẩu bằng PasswordEncoder (bcrypt hoặc tương đương).
        String hash = encoder.hash(cmd.password());
        // Bước 6: tạo aggregate người dùng mới.
        User user = new User(
                UUID.randomUUID(),
                normalized,
                hash,
                // Trạng thái xác minh phụ thuộc vào cờ yêu cầu từ client.
                cmd.verificationRequired() ? User.VerificationState.PENDING : User.VerificationState.VERIFIED,
                new User.LockoutState(false, null),
                0,
                now,
                0L);
        // Bước 7: lưu người dùng vào cơ sở dữ liệu (chưa có OAuth link).
        repo.insert(user, List.of());
        // Bước 8: phát hành sự kiện IdentityUserCreated qua outbox.
        eventPublisher.publish(new IdentityUserCreated(user.id(), normalized, cmd.verificationRequired(), now));
        // Bước 9: ghi log để phục vụ truy vết.
        LOG.info("Registered user id={} verificationRequired={}", user.id(), cmd.verificationRequired());
        return user.id();
    }

    /**
     * Chuẩn hóa email: cắt khoảng trắng và chuyển về chữ thường.
     *
     * @param email email đầu vào.
     * @return email đã chuẩn hóa.
     */
    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    /**
     * Interface trừu tượng hóa đồng hồ. Giúp dễ dàng viết unit test
     * với thời gian giả lập (fake clock) hoặc dùng các loại đồng hồ
     * khác nhau trong tương lai (ví dụ: tick clock).
     */
    public interface Clock { Instant now(); }
}
