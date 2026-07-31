package com.familya.identity.application.usecase;

import com.familya.identity.application.port.in.RevokeSessionCommand;
import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.identity.domain.model.Session;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.NotFoundException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case thu hồi phiên đăng nhập của người dùng.
 *
 * <p>Use case thực hiện các bước:
 * <ol>
 *     <li>Tăng metric "mutation accepted".</li>
 *     <li>Tra cứu phiên. Nếu không tồn tại -> ném
 *         {@link NotFoundException}.</li>
 *     <li>Kiểm tra quyền sở hữu: người dùng hiện tại phải là chủ
 *         của phiên. Nếu không -> ném {@link ForbiddenException}.</li>
 *     <li>Đánh dấu thu hồi qua {@link Session#revoke()} và lưu
 *         lại trong cơ sở dữ liệu.</li>
 *     <li>Ghi log.</li>
 * </ol>
 *
 * <p>Việc kiểm tra quyền sở hữu ở tầng use case thay vì chỉ dựa vào
 * filter bảo mật giúp đảm bảo nguyên tắc "defense in depth" – ngay
 * cả khi filter có lỗ hổng, use case vẫn bảo vệ tài nguyên của
 * người dùng.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@Service
public class RevokeSessionUseCase {

    /** Logger ghi lại sự kiện thu hồi phiên. */
    private static final Logger LOG = LoggerFactory.getLogger(RevokeSessionUseCase.class);

    /** Cổng dữ liệu identity. */
    private final IdentityRepository repo;
    /** Bộ thu thập metric. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case.
     *
     * @param repo    cổng dữ liệu.
     * @param metrics bộ thu thập metric.
     */
    public RevokeSessionUseCase(IdentityRepository repo, PlatformMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    /**
     * Thực hiện thu hồi phiên.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Tăng metric "mutation accepted".</li>
     *     <li>Tra cứu phiên qua {@link IdentityRepository#findSession}.
     *         Nếu không tồn tại -> ném {@link NotFoundException} với
     *         mã 404.</li>
     *     <li>Kiểm tra {@code session.userId()} có trùng với
     *         {@code actingUserId} không. Nếu khác -> ném
     *         {@link ForbiddenException} với mã 403.</li>
     *     <li>Đánh dấu thu hồi qua {@link Session#revoke()} và lưu
     *         lại trạng thái mới.</li>
     *     <li>Ghi log.</li>
     * </ol>
     *
     * @param cmd command chứa {@code sessionId} và {@code actingUserId}.
     * @throws NotFoundException  nếu phiên không tồn tại (HTTP 404).
     * @throws ForbiddenException nếu phiên không thuộc về người dùng hiện tại (HTTP 403).
     */
    @Transactional
    public void execute(RevokeSessionCommand cmd) {
        // Bước 1: ghi nhận metric "mutation accepted".
        metrics.mutationAcceptedCounter("identity-service", "revokeSession").increment();
        // Bước 2: tra cứu phiên.
        Session s = repo.findSession(cmd.sessionId())
                .orElseThrow(() -> new NotFoundException("session " + cmd.sessionId()));
        // Bước 3: đảm bảo người dùng hiện tại là chủ sở hữu phiên.
        if (!s.userId().equals(cmd.actingUserId())) {
            throw new ForbiddenException("Session does not belong to acting user.");
        }
        // Bước 4: đánh dấu thu hồi và lưu lại.
        repo.updateSession(s.revoke());
        // Bước 5: ghi log phục vụ audit / truy vết.
        LOG.info("Revoked session id={} userId={}", s.id(), s.userId());
    }
}
