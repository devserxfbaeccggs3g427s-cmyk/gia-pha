package com.familya.media.application.usecase;

import com.familya.media.application.port.in.ScanAndPromoteCommand;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaBinaryReplicationRepository;
import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.domain.event.MediaActivated;
import com.familya.media.domain.event.MediaScanned;
import com.familya.media.domain.exception.MediaNotFoundException;
import com.familya.media.domain.exception.ScannerUnavailableException;
import com.familya.media.domain.model.MediaAsset;
import com.familya.media.domain.model.MediaAsset.Status;
import com.familya.media.domain.model.ScannerResult;
import com.familya.media.application.port.out.ScannerGateway;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.OptimisticConcurrencyException;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case quét virus / malware cho một media đã upload xong và (nếu sạch)
 * chuyển sang trạng thái {@code READY}.
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Ghi nhận metric mutation.</li>
 *   <li>Tra cứu {@code MediaAsset}; nếu không tồn tại → ném
 *       {@code MediaNotFoundException}.</li>
 *   <li>Phân quyền trên tree (kèm revision kỳ vọng).</li>
 *   <li>Đối chiếu {@code expectedVersion}; lệch → ném
 *       {@code OptimisticConcurrencyException}.</li>
 *   <li>Đảm bảo media đang ở trạng thái {@code QUARANTINED} hoặc
 *       {@code SCANNING}; nếu không → ném {@code IllegalStateException}
 *       (đã qua verify trước đó).</li>
 *   <li>Gọi {@link ScannerGateway}. Triển khai fail-closed:
 *       <ul>
 *         <li>{@code FAILED} (outage): mark FAILED, phát event, ném
 *             {@code ScannerUnavailableException} để caller retry.</li>
 *         <li>{@code INFECTED}: mark FAILED với evidence, phát event, trả
 *             về bình thường (không ném) để caller biết "đã xử lý".</li>
 *         <li>{@code CLEAN}: mark READY, phát {@code MediaScanned} rồi
 *             {@code MediaActivated}, ghi yêu cầu replicate sang region
 *             phụ.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@Service
public class ScanAndPromoteUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ScanAndPromoteUseCase.class);

    private final MediaRepository repo;
    private final ScannerGateway scanner;
    private final MediaAuthorization authz;
    private final MediaChangePublisher publisher;
    private final MediaBinaryReplicationRepository replication;
    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    public ScanAndPromoteUseCase(MediaRepository repo,
                                  ScannerGateway scanner,
                                  MediaAuthorization authz,
                                  MediaChangePublisher publisher,
                                  MediaBinaryReplicationRepository replication,
                                  OutboxWriter outbox,
                                  PlatformMetrics metrics) {
        this.repo = repo;
        this.scanner = scanner;
        this.authz = authz;
        this.publisher = publisher;
        this.replication = replication;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    /**
     * Thực thi use case.
     *
     * @param cmd lệnh scan/promote; xem {@link ScanAndPromoteCommand}.
     * @throws MediaNotFoundException         nếu không tìm thấy media.
     * @throws ForbiddenException             nếu user không có quyền.
     * @throws OptimisticConcurrencyException nếu version không khớp.
     * @throws IllegalStateException          nếu media không ở trạng thái
     *                                        có thể scan.
     * @throws ScannerUnavailableException    nếu scanner fail-closed.
     */
    @Transactional
    public void execute(ScanAndPromoteCommand cmd) {
        // Bước 1: ghi nhận metric mutation đầu vào.
        metrics.mutationAccepted("media-service", "scanAndPromote");
        // Bước 2: tra cứu media.
        MediaAsset asset = repo.findById(cmd.mediaId())
                .orElseThrow(() -> new MediaNotFoundException("Media " + cmd.mediaId() + " not found"));
        // Bước 3: phân quyền.
        MediaAuthorization.Decision d = authz.authorize(asset.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot scan/promote media: " + d.reason());
        }
        // Bước 4: optimistic concurrency.
        if (asset.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "Media " + cmd.mediaId() + " expected version " + cmd.expectedVersion() + " but found " + asset.version());
        }
        // Bước 5: chỉ chấp nhận scan khi media vừa được verify xong
        // (QUARANTINED) hoặc đang trong lần thử lại (SCANNING).
        if (asset.status() != Status.QUARANTINED && asset.status() != Status.SCANNING) {
            throw new IllegalStateException("Media " + cmd.mediaId() + " is not in a scannable state (" + asset.status() + ")");
        }
        Instant now = Instant.now();
        // Bước 6: gọi ScannerGateway; quy tắc fail-closed áp dụng ở
        // triển khai gateway.
        ScannerResult result = scanner.scan(cmd.mediaId(), asset.quarantinePath(), asset.sha256(),
                asset.mimeType(), asset.byteSize());
        if (result.outcome() == ScannerResult.Outcome.FAILED) {
            // 6a: outage → mark FAILED + phát event + ném exception để
            // caller retry sau backoff.
            repo.markFailed(cmd.mediaId(), asset.version(), now, result.evidence());
            publisher.publish(new MediaScanned(asset.treeId(), cmd.mediaId(), asset.version() + 1, now, "FAILED"));
            throw new ScannerUnavailableException("Scanner unavailable: " + result.evidence());
        }
        if (result.outcome() == ScannerResult.Outcome.INFECTED) {
            // 6b: infected → mark FAILED với evidence, phát event, trả về
            // bình thường (không ném) để caller biết "đã xử lý, không retry".
            repo.markFailed(cmd.mediaId(), asset.version(), now, result.evidence());
            publisher.publish(new MediaScanned(asset.treeId(), cmd.mediaId(), asset.version() + 1, now, "INFECTED"));
            return;
        }
        // 6c: CLEAN → chuyển READY, phát MediaScanned + MediaActivated,
        // ghi yêu cầu replicate sang region phụ (vd. secondary).
        repo.markReady(cmd.mediaId(), asset.version(), now);
        publisher.publish(new MediaScanned(asset.treeId(), cmd.mediaId(), asset.version() + 1, now, "CLEAN"));
        publisher.publish(new MediaActivated(asset.treeId(), cmd.mediaId(), asset.version() + 2, now, now));
        replication.record(cmd.mediaId(), "primary", "secondary", asset.sha256(), "PENDING", now);
        LOG.info("Promoted media mediaId={} tree={}", cmd.mediaId(), asset.treeId());
    }
}
