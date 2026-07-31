package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.in.AdvanceRevisionCommand;
import com.familya.treeaccess.application.port.in.FreezeTreeCommand;
import com.familya.treeaccess.application.port.in.TombstoneTreeCommand;
import com.familya.treeaccess.application.port.in.UnfreezeTreeCommand;
import com.familya.treeaccess.application.port.out.TreeEventPublisher;
import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.event.TreeAdvancedRevision;
import com.familya.treeaccess.domain.event.TreeFrozen;
import com.familya.treeaccess.domain.exception.TreeNotFoundException;
import com.familya.treeaccess.domain.model.AuthorizationProjection;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Lifecycle use cases. Freeze/Unfreeze are reversible; Tombstone is
 * irreversible (physical binary deletion is handled by the Media
 * service after retention deadlines). AdvanceRevision is called by the
 * orchestrator after a participant acknowledges reaching the target.
 */
@Service
public class TreeLifecycleUseCases {

    private static final Logger LOG = LoggerFactory.getLogger(TreeLifecycleUseCases.class);

    /** Kho lưu trữ cây. */
    private final TreeRepository repo;

    /** Bộ publish sự kiện cây. */
    private final TreeEventPublisher publisher;

    /** Metric giám sát. */
    private final PlatformMetrics metrics;

    /** Đồng hồ tiêm được. */
    private final Clock clock;

    /**
     * Khởi tạo use-case vòng đời.
     *
     * @param repo      kho lưu trữ
     * @param publisher bộ publish
     * @param metrics   metric
     * @param clock     đồng hồ
     */
    public TreeLifecycleUseCases(TreeRepository repo, TreeEventPublisher publisher,
                                 PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Đóng băng cây (ADMIN). Phát sự kiện {@link TreeFrozen}.
     *
     * @param cmd lệnh đóng băng
     */
    @Transactional
    public void freeze(FreezeTreeCommand cmd) {
        requireAdmin(cmd.treeId(), cmd.actingUser());
        Tree tree = repo.findTree(cmd.treeId()).orElseThrow(() -> new TreeNotFoundException("Tree " + cmd.treeId() + " not found"));
        tree.freeze(cmd.expectedVersion());
        repo.updateTree(tree);
        Instant now = clock.now();
        publisher.publishTreeEvent(new TreeFrozen(tree.id(), tree.revision(), tree.epoch(), now));
        metrics.mutationAcceptedCounter("tree-access-service", "freezeTree").increment();
        LOG.info("Froze tree={} revision={} actor={}", tree.id(), tree.revision(), cmd.actingUser());
    }

    /**
     * Bỏ đóng băng (ADMIN). Không phát sự kiện riêng — TreeAdvancedRevision sẽ
     * được phát bởi orchestrator nếu có.
     *
     * @param cmd lệnh bỏ đóng băng
     */
    @Transactional
    public void unfreeze(UnfreezeTreeCommand cmd) {
        requireAdmin(cmd.treeId(), cmd.actingUser());
        Tree tree = repo.findTree(cmd.treeId()).orElseThrow(() -> new TreeNotFoundException("Tree " + cmd.treeId() + " not found"));
        tree.unfreeze(cmd.expectedVersion());
        repo.updateTree(tree);
        LOG.info("Unfroze tree={} actor={}", tree.id(), cmd.actingUser());
    }

    /**
     * Đánh dấu tombstone (ADMIN). Bước này không thể đảo ngược.
     *
     * @param cmd lệnh tombstone
     */
    @Transactional
    public void tombstone(TombstoneTreeCommand cmd) {
        requireAdmin(cmd.treeId(), cmd.actingUser());
        Tree tree = repo.findTree(cmd.treeId()).orElseThrow(() -> new TreeNotFoundException("Tree " + cmd.treeId() + " not found"));
        tree.tombstone(cmd.expectedVersion());
        repo.updateTree(tree);
        LOG.info("Tombstoned tree={} actor={}", tree.id(), cmd.actingUser());
    }

    /**
     * Tăng revision/epoch (ADMIN). Yêu cầu cây đang ACTIVE; orchestrator gọi
     * sau khi participant đã ACK đạt barrier.
     *
     * @param cmd lệnh tăng revision
     */
    @Transactional
    public void advanceRevision(AdvanceRevisionCommand cmd) {
        // Orchestrator (Saga participant) gọi; chỉ ADMIN mới được phép và cây phải ACTIVE.
        requireAdmin(cmd.treeId(), cmd.actingUser());
        Tree tree = repo.findTree(cmd.treeId()).orElseThrow(() -> new TreeNotFoundException("Tree " + cmd.treeId() + " not found"));
        tree.requireMutable("advanceRevision");
        tree.advanceRevision(cmd.expectedVersion(), cmd.newRevision(), cmd.newEpoch());
        repo.updateTree(tree);
        Instant now = clock.now();
        publisher.publishTreeEvent(new TreeAdvancedRevision(tree.id(), tree.revision(), tree.epoch(),
                cmd.actingUser(), cmd.reason(), now));
        LOG.info("Advanced tree={} revision={} epoch={} reason={}",
                tree.id(), tree.revision(), tree.epoch(), cmd.reason());
    }

    /**
     * Bắt buộc user có quyền ADMIN trên cây.
     *
     * @param treeId mã cây
     * @param userId UUID người dùng
     * @throws ForbiddenException nếu user không có ADMIN
     */
    private void requireAdmin(UUID treeId, UUID userId) {
        AuthorizationProjection p = repo.findProjection(treeId, userId)
                .orElseThrow(() -> new ForbiddenException("User " + userId + " has no membership on tree " + treeId));
        if (p.revoked() || !p.role().grantsMembership()) {
            throw new ForbiddenException("User " + userId + " lacks ADMIN on tree " + treeId);
        }
    }

    /** Interface đồng hồ. */
    public interface Clock { Instant now(); }
}