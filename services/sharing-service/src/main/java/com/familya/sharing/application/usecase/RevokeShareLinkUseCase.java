package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.in.RevokeShareLinkCommand;
import com.familya.sharing.application.port.out.ShareAuthorization;
import com.familya.sharing.application.port.out.ShareChangePublisher;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.exception.ShareNotFoundException;
import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.OptimisticConcurrencyException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case thu hồi (revoke) một liên kết chia sẻ đơn lẻ.
 * <p>
 * Luồng xử lý:
 * <ol>
 *     <li>Ghi nhận metric mutation.</li>
 *     <li>Tìm liên kết theo định danh; ném {@link ShareNotFoundException} nếu không có.</li>
 *     <li>Phân quyền: người dùng phải có quyền thu hồi trên cây.</li>
 *     <li>Kiểm tra optimistic concurrency bằng cách so sánh {@code expectedVersion}.</li>
 *     <li>Tạo bản ghi đã thu hồi với {@code revokedAt}, {@code revocationReason} và
 *         tăng {@code version}, ghi vào DB.</li>
 *     <li>Phát hành sự kiện {@code ShareLinkRevoked} qua outbox.</li>
 * </ol>
 *
 * @author gia-pha platform team
 */
@Service
public class RevokeShareLinkUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RevokeShareLinkUseCase.class);

    private final ShareLinkRepository repo;
    private final ShareAuthorization authz;
    private final ShareChangePublisher publisher;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case.
     *
     * @param repo      kho lưu trữ liên kết chia sẻ.
     * @param authz     cổng phân quyền.
     * @param publisher cổng phát hành sự kiện.
     * @param metrics   bộ thu thập metric.
     */
    public RevokeShareLinkUseCase(ShareLinkRepository repo, ShareAuthorization authz,
                                  ShareChangePublisher publisher, PlatformMetrics metrics) {
        this.repo = repo;
        this.authz = authz;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    /**
     * Thực thi thu hồi liên kết.
     *
     * @param cmd lệnh thu hồi {@link RevokeShareLinkCommand}.
     * @throws ShareNotFoundException         nếu không tìm thấy liên kết.
     * @throws ForbiddenException             nếu người dùng không có quyền thu hồi.
     * @throws OptimisticConcurrencyException nếu {@code expectedVersion} không khớp.
     */
    @Transactional
    public void execute(RevokeShareLinkCommand cmd) {
        // Bước 1: Ghi nhận metric.
        metrics.mutationAccepted("sharing-service", "revokeShareLink");

        // Bước 2: Tìm liên kết theo ID.
        ShareLink link = repo.findById(cmd.shareId())
                .orElseThrow(() -> new ShareNotFoundException("Share " + cmd.shareId() + " not found"));

        // Bước 3: Phân quyền.
        ShareAuthorization.Decision d = authz.authorize(link.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot revoke share link: " + d.reason());
        }

        // Bước 4: Kiểm tra optimistic concurrency &mdash; phiên bản phải khớp.
        if (link.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "Share " + cmd.shareId() + " expected version " + cmd.expectedVersion() + " but found " + link.version());
        }

        // Bước 5: Tạo bản ghi đã thu hồi với thời điểm hiện tại, lý do thu hồi và tăng version.
        Instant now = Instant.now();
        ShareLink revoked = new ShareLink(
                link.id(), link.treeId(), link.scope(), link.targetId(), link.role(),
                link.tokenHash(), link.createdByUserId(), link.createdAt(), link.expiresAt(),
                now, cmd.reason(), link.revision(), link.version() + 1);
        repo.update(revoked);

        // Bước 6: Phát hành sự kiện ShareLinkRevoked.
        publisher.shareLinkRevoked(revoked);

        LOG.info("Revoked share link id={} tree={} reason={}", cmd.shareId(), link.treeId(), cmd.reason());
    }
}