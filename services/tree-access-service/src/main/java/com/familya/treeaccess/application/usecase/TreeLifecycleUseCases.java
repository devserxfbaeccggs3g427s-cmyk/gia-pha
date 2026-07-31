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

    private final TreeRepository repo;
    private final TreeEventPublisher publisher;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public TreeLifecycleUseCases(TreeRepository repo, TreeEventPublisher publisher,
                                 PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

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

    @Transactional
    public void unfreeze(UnfreezeTreeCommand cmd) {
        requireAdmin(cmd.treeId(), cmd.actingUser());
        Tree tree = repo.findTree(cmd.treeId()).orElseThrow(() -> new TreeNotFoundException("Tree " + cmd.treeId() + " not found"));
        tree.unfreeze(cmd.expectedVersion());
        repo.updateTree(tree);
        LOG.info("Unfroze tree={} actor={}", tree.id(), cmd.actingUser());
    }

    @Transactional
    public void tombstone(TombstoneTreeCommand cmd) {
        requireAdmin(cmd.treeId(), cmd.actingUser());
        Tree tree = repo.findTree(cmd.treeId()).orElseThrow(() -> new TreeNotFoundException("Tree " + cmd.treeId() + " not found"));
        tree.tombstone(cmd.expectedVersion());
        repo.updateTree(tree);
        LOG.info("Tombstoned tree={} actor={}", tree.id(), cmd.actingUser());
    }

    @Transactional
    public void advanceRevision(AdvanceRevisionCommand cmd) {
        // The orchestrator (Saga participant) issues this; only ADMINs
        // may trigger, and the tree must be ACTIVE.
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

    private void requireAdmin(UUID treeId, UUID userId) {
        AuthorizationProjection p = repo.findProjection(treeId, userId)
                .orElseThrow(() -> new ForbiddenException("User " + userId + " has no membership on tree " + treeId));
        if (p.revoked() || !p.role().grantsMembership()) {
            throw new ForbiddenException("User " + userId + " lacks ADMIN on tree " + treeId);
        }
    }

    public interface Clock { Instant now(); }
}