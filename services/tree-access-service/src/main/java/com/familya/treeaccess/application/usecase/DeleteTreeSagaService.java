package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.in.InitiateDeleteTreeCommand;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaGateway;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaRepository;
import com.familya.treeaccess.application.port.out.TreeEventPublisher;
import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.event.TreeAdvancedRevision;
import com.familya.treeaccess.domain.event.TreeFrozen;
import com.familya.treeaccess.domain.exception.TreeNotFoundException;
import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.treeaccess.domain.model.AuthorizationProjection;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.OptimisticConcurrencyException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns the delete-tree Saga (ADR-003). The Tree Access service is the only
 * service that may issue tree-wide freeze/tombstone/finalize transitions. The
 * participant sequence is deterministic and ordered by sequenceNo.
 */
@Service
public class DeleteTreeSagaService {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaService.class);

    static final Duration DEFAULT_DEADLINE = Duration.ofMinutes(60);
    static final int     DEFAULT_MAX_ATTEMPTS = 5;

    private final DeleteTreeSagaRepository sagaRepo;
    private final DeleteTreeSagaGateway gateway;
    private final TreeRepository treeRepo;
    private final TreeEventPublisher publisher;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public DeleteTreeSagaService(DeleteTreeSagaRepository sagaRepo,
                                 DeleteTreeSagaGateway gateway,
                                 TreeRepository treeRepo,
                                 TreeEventPublisher publisher,
                                 PlatformMetrics metrics,
                                 Clock clock) {
        this.sagaRepo = sagaRepo;
        this.gateway = gateway;
        this.treeRepo = treeRepo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public UUID initiate(InitiateDeleteTreeCommand cmd) {
        metrics.mutationAccepted("tree-access-service", "deleteTree");
        Tree tree = treeRepo.findTree(cmd.treeId())
                .orElseThrow(() -> new TreeNotFoundException("Tree " + cmd.treeId() + " not found"));

        if (tree.isTombstoned()) {
            throw new OptimisticConcurrencyException("Tree " + tree.id() + " is already tombstoned");
        }
        requireOwnerOrAdmin(tree, cmd.actingUser());

        Instant now = clock.now();
        UUID operationId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        long targetRevision = tree.revision() + 1;
        long targetEpoch = tree.epoch() + 1;

        DeleteTreeSagaState state = new DeleteTreeSagaState(
                operationId, cmd.treeId(), cmd.actingUser(), correlationId,
                DeleteTreeSagaState.State.PENDING,
                targetRevision, targetEpoch,
                now.plus(DEFAULT_DEADLINE), now, null, now, null, null, null);

        // Sequence: own freeze -> own tombstone -> participant fan-out ->
        // participant barriers -> own finalize. Step 1/2/9 are local transitions.
        List<DeleteTreeSagaStep> steps = List.of(
                step(operationId, 1,  "FREEZE_TREE",              "tree-access-service", true,  true),
                step(operationId, 2,  "TOMBSTONE_TREE",           "tree-access-service", true,  true),
                step(operationId, 3,  "PURGE_MEMBER_TREE",        "member-service",      true,  true),
                step(operationId, 4,  "PURGE_RELATIONSHIP_TREE",  "relationship-service",true,  true),
                step(operationId, 5,  "PURGE_EVENT_TREE",         "event-service",       true,  true),
                step(operationId, 6,  "PURGE_MEDIA_METADATA_TREE","media-service",       true,  true),
                step(operationId, 7,  "REVOKE_SHARING_TREE",      "sharing-service",     true,  true),
                step(operationId, 8,  "PURGE_SEARCH_TREE",        "search-service",      true,  true),
                step(operationId, 9,  "FINALIZE_TREE_DELETION",   "tree-access-service", true,  false));

        sagaRepo.saveState(state);
        sagaRepo.saveSteps(steps);

        // Step 1 (FREEZE_TREE) is owner-local: ACTIVE -> FROZEN so writes
        // are blocked immediately. The tombstone is deferred to Step 9
        // (FINALIZE_TREE_DELETION) so that rollback can still unhide the
        // tree before the irreversible boundary.
        tree.freeze(cmd.expectedTreeVersion());
        treeRepo.updateTree(tree);
        publisher.publishTreeEvent(new TreeFrozen(tree.id(), tree.revision(), tree.epoch(), now));

        publisher.publishTreeEvent(new TreeAdvancedRevision(
                tree.id(), tree.revision(), tree.epoch(),
                operationId, "delete-tree-saga:freeze", now));

        state.transitionTo(DeleteTreeSagaState.State.FREEZING, now);
        state.transitionTo(DeleteTreeSagaState.State.TOMBSTONING, now);

        gateway.stageOperationStarted(state);
        gateway.stageFirstStep(state, steps.get(2)); // first participant step
        state.transitionTo(DeleteTreeSagaState.State.PURGING, now);
        sagaRepo.saveState(state);

        LOG.info("Initiated delete-tree Saga operationId={} treeId={}", operationId, tree.id());
        return operationId;
    }

    private void requireOwnerOrAdmin(Tree tree, UUID userId) {
        if (userId == null) {
            throw new ForbiddenException("Missing acting user");
        }
        if (tree.ownerUserId().equals(userId)) return;
        AuthorizationProjection projection = treeRepo.findProjection(tree.id(), userId)
                .orElseThrow(() -> new ForbiddenException(
                        "User " + userId + " has no membership on tree " + tree.id()));
        if (projection.revoked() || projection.role() == null
                || !projection.role().grantsMembership()) {
            throw new ForbiddenException(
                    "User " + userId + " lacks ADMIN on tree " + tree.id());
        }
    }

    private static DeleteTreeSagaStep step(UUID operationId, int seq, String code,
                                           String participant, boolean required,
                                           boolean compensatable) {
        return new DeleteTreeSagaStep(operationId, seq, code, participant,
                required, compensatable,
                DeleteTreeSagaStep.State.PENDING, 0, DEFAULT_MAX_ATTEMPTS,
                null, null, null, null, null, null);
    }

    public interface Clock { Instant now(); }
}