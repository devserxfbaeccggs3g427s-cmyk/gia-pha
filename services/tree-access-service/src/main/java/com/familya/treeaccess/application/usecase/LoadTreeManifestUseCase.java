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

    private final TreeRepository repo;
    private final PlatformMetrics metrics;

    public LoadTreeManifestUseCase(TreeRepository repo, PlatformMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

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

    public record LoadResult(java.util.UUID treeId, Status status) {
        public enum Status { LOADED, DUPLICATE }
    }
}