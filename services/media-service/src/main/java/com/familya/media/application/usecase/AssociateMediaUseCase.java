package com.familya.media.application.usecase;

import com.familya.media.application.port.in.AssociateMediaCommand;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.application.port.out.MediaReferenceRepository;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.application.port.out.ReferenceAvailability;
import com.familya.media.domain.event.MediaAssociated;
import com.familya.media.domain.exception.MediaNotFoundException;
import com.familya.media.domain.exception.ReferenceUnavailableException;
import com.familya.media.domain.model.MediaAsset;
import com.familya.media.domain.model.MediaAsset.Status;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.OptimisticConcurrencyException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case gắn một media (đang ở trạng thái {@code READY}) vào một đối
 * tượng nghiệp vụ (MEMBER / EVENT / ALBUM).
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Ghi nhận metric mutation được chấp nhận.</li>
 *   <li>Tra cứu {@code MediaAsset} theo {@code mediaId}; nếu không tồn
 *       tại → {@code MediaNotFoundException}.</li>
 *   <li>Kiểm tra quyền của {@code actingUser} trên tree của media (kèm
 *       revision kỳ vọng); nếu bị từ chối → {@code ForbiddenException}.</li>
 *   <li>Đối chiếu {@code expectedVersion} với version hiện tại của
 *       media; lệch → {@code OptimisticConcurrencyException}.</li>
 *   <li>Nếu media chưa ở {@code READY}: ghi reference {@code PENDING} với
 *       errorCode {@code media-not-ready} rồi ném
 *       {@code ReferenceUnavailableException} để caller biết phải retry
 *       sau.</li>
 *   <li>Kiểm tra target (member/event/album) còn khả dụng qua
 *       {@code ReferenceAvailability}. Khả dụng nghĩa là target còn
 *       trong projection.</li>
 *   <li>Nếu target không khả dụng: ghi reference {@code PENDING} với
 *       errorCode {@code reference-unavailable} rồi ném
 *       {@code ReferenceUnavailableException}.</li>
 *   <li>Nếu mọi điều kiện OK: ghi reference {@code ACTIVE}, phát sự kiện
 *       {@code MediaAssociated} để downstream cập nhật index/projection.</li>
 * </ol>
 *
 * <p>Lưu ý: trong trường hợp lỗi "chưa sẵn sàng" hoặc "target không
 * khả dụng" reference vẫn được ghi ở {@code PENDING} để có dấu vết
 * phục vụ retry / audit.</p>
 */
@Service
public class AssociateMediaUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(AssociateMediaUseCase.class);

    private final MediaRepository repo;
    private final MediaReferenceRepository refs;
    private final ReferenceAvailability availability;
    private final MediaAuthorization authz;
    private final MediaChangePublisher publisher;
    private final PlatformMetrics metrics;

    public AssociateMediaUseCase(MediaRepository repo,
                                  MediaReferenceRepository refs,
                                  ReferenceAvailability availability,
                                  MediaAuthorization authz,
                                  MediaChangePublisher publisher,
                                  PlatformMetrics metrics) {
        this.repo = repo;
        this.refs = refs;
        this.availability = availability;
        this.authz = authz;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    /**
     * Thực thi use case.
     *
     * @param cmd lệnh gắn media; xem {@link AssociateMediaCommand}.
     * @throws MediaNotFoundException           nếu không tìm thấy media.
     * @throws ForbiddenException               nếu user không có quyền.
     * @throws OptimisticConcurrencyException   nếu version media đã thay đổi.
     * @throws ReferenceUnavailableException    nếu media chưa READY hoặc
     *                                         target không khả dụng.
     */
    @Transactional
    public void execute(AssociateMediaCommand cmd) {
        // Bước 1: ghi nhận metric để dashboard thấy được use case đã được
        // chấp nhận xử lý (chưa phản ánh kết quả thành công / thất bại).
        metrics.mutationAccepted("media-service", "associate");
        // Bước 2: tra cứu media; throw sớm nếu không tồn tại để tránh
        // thực hiện phép kiểm tra quyền trên thực thể ma.
        MediaAsset asset = repo.findById(cmd.mediaId())
                .orElseThrow(() -> new MediaNotFoundException("Media " + cmd.mediaId() + " not found"));
        // Bước 3: kiểm tra quyền kèm revision kỳ vọng → phát hiện quyền
        // bị thu hồi trong lúc xử lý (chống TOCTOU).
        MediaAuthorization.Decision d = authz.authorize(asset.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot associate media: " + d.reason());
        }
        // Bước 4: optimistic concurrency trên MediaAsset; giúp phát hiện
        // mutation song song từ use case khác (vd. tombstone).
        if (asset.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "Media " + cmd.mediaId() + " expected version " + cmd.expectedVersion() + " but found " + asset.version());
        }
        // Bước 5: chỉ cho phép gắn khi media đã READY. Vẫn ghi PENDING
        // để caller / audit biết rằng đã có attempt.
        if (asset.status() != Status.READY) {
            refs.upsert(cmd.mediaId(), asset.treeId(), cmd.targetKind(), cmd.targetId(),
                    "PENDING", Instant.now(), "media-not-ready");
            throw new ReferenceUnavailableException(
                    "Media " + cmd.mediaId() + " is not READY (status=" + asset.status() + ")");
        }
        // Bước 6: kiểm tra target còn sống trong projection tương ứng;
        // switch theo kind để gọi đúng port. default → false (không hỗ
        // trợ kind lạ).
        boolean available = switch (cmd.targetKind()) {
            case "MEMBER" -> availability.isMemberAvailable(asset.treeId(), cmd.targetId());
            case "EVENT" -> availability.isEventAvailable(asset.treeId(), cmd.targetId());
            case "ALBUM" -> availability.isAlbumAvailable(asset.treeId(), cmd.targetId());
            default -> false;
        };
        if (!available) {
            // Bước 7: ghi PENDING với errorCode cụ thể rồi ném exception
            // để caller có thể retry khi target "sống lại".
            refs.upsert(cmd.mediaId(), asset.treeId(), cmd.targetKind(), cmd.targetId(),
                    "PENDING", Instant.now(), "reference-unavailable");
            throw new ReferenceUnavailableException(
                    "Reference target " + cmd.targetKind() + ":" + cmd.targetId() + " not available for tree " + asset.treeId());
        }
        // Bước 8: tham chiếu ACTIVE + phát sự kiện downstream.
        refs.upsert(cmd.mediaId(), asset.treeId(), cmd.targetKind(), cmd.targetId(),
                "ACTIVE", Instant.now(), null);
        publisher.publish(new MediaAssociated(asset.treeId(), cmd.mediaId(), asset.version() + 1,
                Instant.now(), cmd.targetKind(), cmd.targetId()));
        LOG.info("Associated media mediaId={} -> {}:{}", cmd.mediaId(), cmd.targetKind(), cmd.targetId());
    }
}
