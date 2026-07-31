package com.familya.identity.application.usecase;

import com.familya.identity.application.port.in.VerifyEmailCommand;
import com.familya.identity.application.port.out.IdentityEventPublisher;
import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.identity.domain.event.IdentityUserVerified;
import com.familya.identity.domain.exception.InvalidVerificationTokenException;
import com.familya.identity.domain.model.EmailVerificationToken;
import com.familya.identity.domain.model.User;
import com.familya.platform.error.NotFoundException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Use case xác minh địa chỉ email người dùng.
 *
 * <p>Use case thực hiện kiểm tra chặt chẽ để đảm bảo token xác minh
 * hợp lệ và còn hiệu lực trước khi đánh dấu người dùng đã xác minh:
 * <ol>
 *     <li>Tăng metric "mutation accepted".</li>
 *     <li>Lấy thời điểm hiện tại.</li>
 *     <li>Tra cứu token. Nếu không tồn tại -> ném
 *         {@link InvalidVerificationTokenException}.</li>
 *     <li>Kiểm tra token có thuộc về {@code userId} không. Nếu không
 *         -> ném {@link InvalidVerificationTokenException} với lý do
 *         "token mismatch".</li>
 *     <li>Kiểm tra tính khả dụng (chưa sử dụng, chưa hết hạn) qua
 *         {@link EmailVerificationToken#isUsable(Instant)}. Nếu không
 *         hợp lệ -> ném {@link InvalidVerificationTokenException}.</li>
 *     <li>Tra cứu người dùng. Nếu không tồn tại -> ném
 *         {@link NotFoundException}.</li>
 *     <li>Chuyển trạng thái xác minh sang {@link User.VerificationState#VERIFIED}
 *         và reset số lần đăng nhập thất bại.</li>
 *     <li>Đánh dấu token đã được sử dụng.</li>
 *     <li>Phát hành sự kiện {@link IdentityUserVerified}.</li>
 * </ol>
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@Service
public class VerifyEmailUseCase {

    /** Logger ghi lại quá trình xác minh. */
    private static final Logger LOG = LoggerFactory.getLogger(VerifyEmailUseCase.class);

    /** Cổng dữ liệu identity. */
    private final IdentityRepository repo;
    /** Bộ phát hành sự kiện. */
    private final IdentityEventPublisher eventPublisher;
    /** Bộ thu thập metric. */
    private final PlatformMetrics metrics;
    /** Đồng hồ. */
    private final RegisterUserUseCase.Clock clock;

    /**
     * Khởi tạo use case.
     *
     * @param repo           cổng dữ liệu.
     * @param eventPublisher bộ phát hành sự kiện.
     * @param metrics        bộ thu thập metric.
     * @param clock          đồng hồ.
     */
    public VerifyEmailUseCase(IdentityRepository repo,
                              IdentityEventPublisher eventPublisher,
                              PlatformMetrics metrics,
                              RegisterUserUseCase.Clock clock) {
        this.repo = repo;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Thực hiện xác minh email.
     *
     * <p>Quy trình xử lý được minh họa chi tiết ở phần mô tả lớp.
     *
     * @param cmd command chứa {@code userId} và {@code token}.
     * @return UUID người dùng đã được xác minh.
     * @throws InvalidVerificationTokenException nếu token không tồn tại,
     *         không khớp với userId, đã được sử dụng hoặc đã hết hạn.
     * @throws NotFoundException                  nếu người dùng không tồn tại.
     */
    @Transactional
    public UUID execute(VerifyEmailCommand cmd) {
        // Bước 1: ghi nhận metric "mutation accepted".
        metrics.mutationAcceptedCounter("identity-service", "verifyEmail").increment();
        // Bước 2: lấy thời điểm hiện tại.
        Instant now = clock.now();
        // Bước 3: tra cứu token trong cơ sở dữ liệu.
        EmailVerificationToken stored = repo.findEmailVerificationToken(cmd.token())
                .orElseThrow(() -> new InvalidVerificationTokenException("no verification token"));
        // Bước 4: kiểm tra token có thuộc về userId không.
        if (!stored.userId().equals(cmd.userId())) {
            metrics.mutationFailed("identity-service", "verifyEmail", "token.mismatch");
            throw new InvalidVerificationTokenException("token mismatch");
        }
        // Bước 5: kiểm tra token còn khả dụng (chưa sử dụng, chưa hết hạn).
        if (!stored.isUsable(now)) {
            metrics.mutationFailed("identity-service", "verifyEmail", "token.expired");
            throw new InvalidVerificationTokenException("token expired or consumed");
        }
        // Bước 6: tra cứu người dùng.
        User user = repo.findById(cmd.userId())
                .orElseThrow(() -> new NotFoundException("user " + cmd.userId()));
        // Bước 7: chuyển trạng thái sang VERIFIED và reset số lần đăng nhập thất bại.
        User verified = user.verified(now);
        // Bước 8: lưu thay đổi vào cơ sở dữ liệu.
        repo.update(verified);
        // Bước 9: đánh dấu token đã được sử dụng (chống tái sử dụng).
        repo.consumeEmailVerificationToken(cmd.token(), now);
        // Bước 10: phát hành sự kiện IdentityUserVerified.
        eventPublisher.publish(new IdentityUserVerified(verified.id(), now));
        // Bước 11: log để phục vụ truy vết.
        LOG.info("Verified user id={}", verified.id());
        return verified.id();
    }
}
