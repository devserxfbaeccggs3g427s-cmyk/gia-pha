package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.in.GrantMembershipCommand;
import com.familya.treeaccess.application.port.out.TreeEventPublisher;
import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.event.MembershipGranted;
import com.familya.treeaccess.domain.exception.OwnerImmutableException;
import com.familya.treeaccess.domain.exception.TreeFrozenException;
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
 * Grants a membership role. The grantor MUST already hold ADMIN on the
 * tree (owner always ADMIN). Granting a non-ADMIN role to the owner is
 * rejected by the owner-immutability invariant.
 */
@Service
public class GrantMembershipUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(GrantMembershipUseCase.class);

    private final TreeRepository repo;
    private final TreeEventPublisher publisher;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public GrantMembershipUseCase(TreeRepository repo, TreeEventPublisher publisher,
                                  PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(GrantMembershipCommand cmd) {
        metrics.mutationAcceptedCounter("tree-access-service", "grantMembership").increment();
        Tree tree = repo.findTree(cmd.treeId()).orElseThrow(() -> new TreeNotFoundException("Tree " + cmd.treeId() + " not found"));
        if (tree.isFrozen() || tree.isTombstoned()) {
            throw new TreeFrozenException("Tree " + cmd.treeId() + " is " + tree.state());
        }
        AuthorizationProjection actor = repo.findProjection(cmd.treeId(), cmd.grantedBy())
                .orElseThrow(() -> new ForbiddenException("Actor " + cmd.grantedBy() + " has no membership on tree " + cmd.treeId()));
        if (actor.revoked() || !actor.role().grantsMembership()) {
            throw new ForbiddenException("Actor " + cmd.grantedBy() + " lacks ADMIN on tree " + cmd.treeId());
        }
        // Owner immutability: cannot grant a non-ADMIN role to the owner.
        if (tree.ownerUserId().equals(cmd.userId()) && cmd.role() != TreeMembership.Role.ADMIN) {
            throw new OwnerImmutableException("Cannot downgrade the tree owner from ADMIN");
        }
        Instant now = clock.now();
        var existing = repo.findMembership(cmd.treeId(), cmd.userId());
        UUID membershipId;
        if (existing.isPresent()) {
            TreeMembership prior = existing.get();
            if (prior.isActive()) {
                throw new IllegalStateException("Active membership already exists for user " + cmd.userId());
            }
            TreeMembership reactivated = new TreeMembership(prior.id(), cmd.treeId(), cmd.userId(),
                    cmd.role(), cmd.grantedBy(), now, null, null, null);
            repo.updateMembership(reactivated);
            membershipId = reactivated.id();
        } else {
            TreeMembership fresh = new TreeMembership(UUID.randomUUID(), cmd.treeId(), cmd.userId(),
                    cmd.role(), cmd.grantedBy(), now, null, null, null);
            repo.insertMembership(fresh);
            membershipId = fresh.id();
        }
        repo.upsertProjection(new AuthorizationProjection(
                cmd.treeId(), cmd.userId(), cmd.role(),
                tree.revision(), tree.epoch(), now, false, null, now));
        publisher.publishMembershipEvent(new MembershipGranted(cmd.treeId(), cmd.userId(),
                cmd.role(), cmd.grantedBy(), now));
        LOG.info("Granted role={} user={} tree={} by={}", cmd.role(), cmd.userId(), cmd.treeId(), cmd.grantedBy());
        return membershipId;
    }

    public interface Clock { Instant now(); }
}