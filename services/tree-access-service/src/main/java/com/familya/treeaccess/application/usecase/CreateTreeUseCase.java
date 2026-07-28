package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.in.CreateTreeCommand;
import com.familya.treeaccess.application.port.out.TreeEventPublisher;
import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.event.TreeCreated;
import com.familya.treeaccess.domain.model.AuthorizationProjection;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.treeaccess.domain.model.TreeMembership;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates a tree and an owner membership. The owner is granted
 * effective ADMIN by virtue of tree.ownerUserId matching userId (the
 * membership row carries ADMIN but the invariant is also enforced in
 * {@link TreeMembership.Role#effectiveRole()} via the projection).
 */
@Service
public class CreateTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateTreeUseCase.class);

    private final TreeRepository repo;
    private final TreeEventPublisher publisher;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public CreateTreeUseCase(TreeRepository repo, TreeEventPublisher publisher,
                             PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(CreateTreeCommand cmd) {
        metrics.mutationAcceptedCounter("tree-access-service", "createTree").increment();
        Instant now = clock.now();
        UUID treeId = UUID.randomUUID();
        Tree tree = new Tree(treeId, cmd.name(), cmd.ownerUserId(),
                Tree.State.ACTIVE, 1L, 1L, now, null, null, 0L);
        TreeMembership owner = new TreeMembership(
                UUID.randomUUID(), treeId, cmd.ownerUserId(), TreeMembership.Role.ADMIN,
                cmd.ownerUserId(), now, null, null, null);
        repo.insertTree(tree, owner);
        repo.upsertProjection(new AuthorizationProjection(
                treeId, cmd.ownerUserId(), TreeMembership.Role.ADMIN,
                tree.revision(), tree.epoch(), now, false, null, now));
        publisher.publishTreeEvent(new TreeCreated(treeId, cmd.ownerUserId(), cmd.name(),
                tree.revision(), tree.epoch(), now));
        LOG.info("Created tree id={} owner={} name={}", treeId, cmd.ownerUserId(), cmd.name());
        return treeId;
    }

    public interface Clock { Instant now(); }
}