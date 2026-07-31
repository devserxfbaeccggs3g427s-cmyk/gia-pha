package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.in.LoadTreeManifestCommand;
import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.treeaccess.domain.model.TreeMembership;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent migration loader for the tree-access service. Loads a
 * tree manifest produced by the immutable Blob source extraction.
 * Re-running with the same treeId MUST NOT duplicate effects; the
 * primary-key constraint and {@code replaySafe} flag together enforce
 * exactly-once acceptance.
 */
@Service
public class LoadTreeManifestUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(LoadTreeManifestUseCase.class);

    /** Kho lưu trữ cây. */
    private final TreeRepository repo;

    /** Metric giám sát. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use-case nạp manifest.
     *
     * @param repo    kho lưu trữ
     * @param metrics metric
     */
    public LoadTreeManifestUseCase(TreeRepository repo, PlatformMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    /**
     * Nạp một manifest cây vào cơ sở dữ liệu. Thao tác idempotent:
     *
     * <ol>
     *   <li>Nếu {@code treeId} đã tồn tại → trả {@code DUPLICATE}.</li>
     *   <li>Nếu có hai worker cùng insert → bắt {@link DuplicateKeyException} và trả {@code DUPLICATE}.</li>
     *   <li>Các membership trùng cũng bị bỏ qua qua {@code DuplicateKeyException}.</li>
     * </ol>
     *
     * @param cmd lệnh nạp manifest
     * @return {@link LoadResult} trạng thái nạp
     */
    @Transactional
    public LoadResult execute(LoadTreeManifestCommand cmd) {
        metrics.mutationAccepted("tree-access-service", "loadTreeManifest");
        if (repo.findTree(cmd.treeId()).isPresent()) {
            if (cmd.replaySafe()) {
                LOG.info("Replay-skip tree manifest treeId={}", cmd.treeId());
                return new LoadResult(cmd.treeId(), LoadResult.Status.DUPLICATE);
            }
            return new LoadResult(cmd.treeId(), LoadResult.Status.DUPLICATE);
        }
        Tree tree = new Tree(
                cmd.treeId(), cmd.name(), cmd.ownerUserId(),
                Tree.State.ACTIVE, cmd.initialRevision(), cmd.initialEpoch(),
                cmd.createdAt(), null, null, 0L);
        TreeMembership owner = new TreeMembership(
                java.util.UUID.randomUUID(), cmd.treeId(), cmd.ownerUserId(),
                TreeMembership.Role.ADMIN, cmd.ownerUserId(), cmd.createdAt(), null, null, null);
        try {
            repo.insertTree(tree, owner);
        } catch (DuplicateKeyException dup) {
            // Hai worker cùng chèn — vẫn coi là DUPLICATE để caller có thể tiếp tục.
            LOG.warn("Concurrent insert for treeId={}", cmd.treeId());
            return new LoadResult(cmd.treeId(), LoadResult.Status.DUPLICATE);
        }
        for (var line : cmd.memberships()) {
            TreeMembership m = new TreeMembership(
                    java.util.UUID.randomUUID(), cmd.treeId(), line.userId(),
                    TreeMembership.Role.valueOf(line.role()),
                    line.grantedBy(), line.grantedAt(), null, null, null);
            try {
                repo.insertMembership(m);
            } catch (DuplicateKeyException ignored) { /* idempotent */ }
        }
        LOG.info("Loaded tree manifest treeId={} memberships={}", cmd.treeId(), cmd.memberships().size());
        return new LoadResult(cmd.treeId(), LoadResult.Status.LOADED);
    }

    /**
     * Kết quả nạp manifest.
     *
     * @param treeId mã cây
     * @param status {@link Status#LOADED} hoặc {@link Status#DUPLICATE}
     */
    public record LoadResult(java.util.UUID treeId, Status status) {
        /** Trạng thái nạp: LOADED khi tạo mới, DUPLICATE khi manifest đã tồn tại. */
        public enum Status { LOADED, DUPLICATE }
    }
}