package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.in.TombstoneRelationshipCommand;
import com.familya.relationship.application.port.out.AuthorizationProjectionRepository;
import com.familya.relationship.application.port.out.RelationshipEventPublisher;
import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.event.RelationshipTombstoned;
import com.familya.relationship.domain.exception.RelationshipNotFoundException;
import com.familya.relationship.domain.model.Relationship;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.StaleProjectionException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Use case đánh dấu xóa mềm (tombstone) một quan hệ đã tồn tại.
 * <p>
 * Quy trình xử lý theo các bước:
 * </p>
 * <ol>
 *   <li><b>Đo lường</b> - ghi nhận metric.</li>
 *   <li><b>Tra cứu</b> - tìm quan hệ theo {@code relationshipId}; nếu không có
 *       thì ném {@link RelationshipNotFoundException}.</li>
 *   <li><b>Phân quyền</b> - kiểm tra quyền của {@code actingUser} trên cây
 *       chứa quan hệ.</li>
 *   <li><b>Kiểm tra stale projection</b> - đảm bảo projection phân quyền đã được
 *       client đồng bộ.</li>
 *   <li><b>Tombstone</b> - aggregate tự kiểm tra version (optimistic concurrency)
 *       rồi đánh dấu xóa mềm; cập nhật DB.</li>
 *   <li><b>Command log</b> - ghi lại lệnh để phục vụ tái dựng.</li>
 *   <li><b>Phát sự kiện</b> - {@code RelationshipTombstoned} qua outbox.</li>
 * </ol>
 *
 * <p>
 * Mọi thao tác diễn ra trong cùng một transaction nhờ {@code @Transactional}.
 * </p>
 */
@Service
public class TombstoneRelationshipUseCase {

    /** Logger ghi nhận hoạt động. */
    private static final Logger LOG = LoggerFactory.getLogger(TombstoneRelationshipUseCase.class);

    /** Repository thao tác với cơ sở dữ liệu. */
    private final RelationshipRepository repo;
    /** Cổng phát sự kiện miền (outbox). */
    private final RelationshipEventPublisher publisher;
    /** Projection phân quyền. */
    private final AuthorizationProjectionRepository authRepo;
    /** Bộ đếm metric. */
    private final PlatformMetrics metrics;
    /** Đồng hồ tiêm vào (để test). */
    private final Clock clock;

    /**
     * Khởi tạo use case.
     *
     * @param repo      repository quan hệ
     * @param publisher cổng phát sự kiện
     * @param authRepo  projection phân quyền
     * @param metrics   bộ đếm metric
     * @param clock     đồng hồ tiêm vào
     */
    public TombstoneRelationshipUseCase(RelationshipRepository repo, RelationshipEventPublisher publisher,
                                         AuthorizationProjectionRepository authRepo,
                                         PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authRepo = authRepo;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Thực thi lệnh tombstone.
     *
     * @param cmd lệnh tombstone (chứa relationshipId, actingUser, expectedVersion, expectedTreeRevision)
     * @throws RelationshipNotFoundException nếu quan hệ không tồn tại
     * @throws ForbiddenException            nếu user không có quyền
     * @throws StaleProjectionException      nếu projection phân quyền cũ
     * @throws com.familya.platform.error.OptimisticConcurrencyException
     *         nếu version của quan hệ không khớp {@code expectedVersion}
     */
    @Transactional
    public void execute(TombstoneRelationshipCommand cmd) {
        // Bước 1: ghi nhận metric "mutation được chấp nhận".
        metrics.mutationAccepted("relationship-service", "tombstoneRelationship");

        // Bước 2: tra cứu quan hệ theo ID; nếu không thấy thì ném ngoại lệ.
        Relationship rel = repo.findById(cmd.relationshipId())
                .orElseThrow(() -> new RelationshipNotFoundException("Relationship " + cmd.relationshipId() + " not found"));

        // Bước 3: phân quyền trên cây chứa quan hệ (treeId lấy từ chính aggregate).
        var auth = authRepo.findAuth(rel.treeId(), cmd.actingUser())
                .orElseThrow(() -> new ForbiddenException(
                        "No authorization projection for tree=" + rel.treeId() + " user=" + cmd.actingUser()));
        if (auth.isRevoked() || !auth.canEdit()) {
            throw new ForbiddenException("User " + cmd.actingUser() + " cannot edit tree " + rel.treeId());
        }
        // Bước 4: kiểm tra stale projection.
        if (auth.revision() < cmd.expectedTreeRevision()) {
            throw new StaleProjectionException(
                    "Auth projection revision " + auth.revision() + " < expected " + cmd.expectedTreeRevision());
        }

        // Bước 5: gọi tombstone() trên aggregate - aggregate tự kiểm tra version
        // (optimistic concurrency). Nếu version không khớp sẽ ném OptimisticConcurrencyException.
        Instant now = clock.now();
        rel.tombstone(cmd.expectedVersion(), now);
        repo.update(rel);

        // Bước 6: lấy command seq tiếp theo và ghi command log.
        long seq = repo.nextCommandSeq(rel.treeId());
        repo.appendCommandLog(rel.treeId(), seq, "tombstone_relationship",
                cmd.actingUser(), "", now);

        // Bước 7: phát sự kiện RelationshipTombstoned qua outbox.
        publisher.publish(new RelationshipTombstoned(rel.id(), rel.treeId(), seq, now));
        LOG.info("Tombstoned relationship id={} seq={} actingUser={}", rel.id(), seq, cmd.actingUser());
    }

    /** Interface đồng hồ tiêm vào để dễ kiểm thử. */
    public interface Clock { Instant now(); }
}