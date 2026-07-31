package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.in.RevokeMembershipCommand;
import com.familya.treeaccess.application.port.out.TreeEventPublisher;
import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.event.MembershipRevoked;
import com.familya.treeaccess.domain.exception.MembershipNotFoundException;
import com.familya.treeaccess.domain.exception.OwnerImmutableException;
import com.familya.treeaccess.domain.exception.TreeNotFoundException;
import com.familya.treeaccess.domain.model.AuthorizationProjection;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.treeaccess.domain.model.TreeMembership;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revokes a membership. The revoker MUST hold ADMIN. Revoking the tree
 * owner's membership is rejected by the owner-immutability invariant
 * even if such a row exists.
 */
@Service
public class RevokeMembershipUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RevokeMembershipUseCase.class);

    private final TreeRepository repo;
    private final TreeEventPublisher publisher;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public RevokeMembershipUseCase(TreeRepository repo, TreeEventPublisher publisher,
                                   PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void execute(RevokeMembershipCommand cmd) {
        metrics.mutationAcceptedCounter("tree-access-service", "revokeMembership").increment();
        Tree tree = repo.findTree(cmd.treeId()).orElseThrow(() -> new TreeNotFoundException("Tree " + cmd.treeId() + " not found"));
        if (tree.ownerUserId().equals(cmd.userId())) {
            throw new OwnerImmutableException("Cannot revoke the tree owner");
        }
        AuthorizationProjection actor = repo.findProjection(cmd.treeId(), cmd.revokedBy())
                .orElseThrow(() -> new ForbiddenException("Actor " + cmd.revokedBy() + " has no membership on tree " + cmd.treeId()));
        if (actor.revoked() || !actor.role().grantsMembership()) {
            throw new ForbiddenException("Actor " + cmd.revokedBy() + " lacks ADMIN on tree " + cmd.treeId());
        }
        TreeMembership m = repo.findMembership(cmd.treeId(), cmd.userId())
                .orElseThrow(() -> new MembershipNotFoundException("No membership for user " + cmd.userId()));
        if (!m.isActive()) {
            return;
        }
        m.revoke(cmd.revokedBy(), cmd.reason());
        repo.updateMembership(m);
        Instant now = clock.now();
        repo.upsertProjection(new AuthorizationProjection(
                cmd.treeId(), cmd.userId(), null,
                tree.revision(), tree.epoch(), m.grantedAt(), true, null, now));
        publisher.publishMembershipEvent(new MembershipRevoked(cmd.treeId(), cmd.userId(),
                cmd.revokedBy(), cmd.reason(), now));
        LOG.info("Revoked user={} tree={} by={} reason={}", cmd.userId(), cmd.treeId(), cmd.revokedBy(), cmd.reason());
    }

    public interface Clock { Instant now(); }
}