package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.in.DeleteTreeSagaReplyCommand;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaGateway;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaRepository;
import com.familya.treeaccess.application.port.out.TreeEventPublisher;
import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.event.TreeAdvancedRevision;
import com.familya.treeaccess.domain.exception.TreeNotFoundException;
import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner-side processor for delete-tree participant replies. Performs the
 * per-step barrier check (aggregate version + epoch), advances the Saga to
 * the next step or {@code FINALIZING}, and dispatches compensation on
 * failure. Tree Access is the only service that performs the
 * {@code FINALIZE_TREE_DELETION} step; this method runs that step in the
 * same transaction as the reply handling so the tree is tombstoned only
 * after every required participant has acked.
 */
@Service
public class DeleteTreeSagaReplyProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaReplyProcessor.class);

    private final DeleteTreeSagaRepository sagaRepo;
    private final DeleteTreeSagaGateway gateway;
    private final TreeRepository treeRepo;
    private final TreeEventPublisher publisher;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public DeleteTreeSagaReplyProcessor(DeleteTreeSagaRepository sagaRepo,
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
    public void process(DeleteTreeSagaReplyCommand cmd) {
        Optional<DeleteTreeSagaState> opt = sagaRepo.findState(cmd.operationId());
        if (opt.isEmpty()) {
            LOG.warn("Ignoring reply for unknown delete-tree operationId={}", cmd.operationId());
            return;
        }
        DeleteTreeSagaState state = opt.get();
        if (state.state().isTerminal()) {
            LOG.info("Ignoring reply for terminal delete-tree Saga operationId={} state={}",
                    state.operationId(), state.state());
            return;
        }

        List<DeleteTreeSagaStep> steps = sagaRepo.listSteps(cmd.operationId());
        DeleteTreeSagaStep current = steps.stream()
                .filter(s -> s.stepCode().equals(cmd.stepCode()))
                .filter(s -> s.participantService().equals(cmd.participantService()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No step " + cmd.stepCode() + " for " + cmd.participantService()));

        Instant now = clock.now();
        metrics.mutationAccepted("tree-access-service", "deleteTree.reply");

        if (cmd.failed()) {
            current.fail(cmd.failureCode(), cmd.failureMessage(), now);
            sagaRepo.updateStep(current);
            state.recordFailure(cmd.failureCode(), cmd.failureMessage(), now);
            if (state.state() != DeleteTreeSagaState.State.COMPENSATING) {
                state.transitionTo(DeleteTreeSagaState.State.COMPENSATING, now);
            }
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            compensatePreviousSteps(state, steps, current);
            return;
        }

        if (!satisfiesBarrier(state, cmd)) {
            current.fail("BARRIER_NOT_MET",
                    "applied aggregateVersion=" + cmd.appliedAggregateVersion()
                            + " epoch=" + cmd.appliedEpoch() + " below target", now);
            sagaRepo.updateStep(current);
            state.recordFailure("BARRIER_NOT_MET",
                    "step " + current.stepCode() + " did not meet barrier", now);
            state.transitionTo(DeleteTreeSagaState.State.MANUAL_REVIEW, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            return;
        }

        current.ack(now, cmd.appliedAggregateVersion(), cmd.appliedEpoch());
        sagaRepo.updateStep(current);

        // FINALIZE is the owner-local barrier-close step: tombstone the tree,
        // emit the final TreeAdvancedRevision, and mark the Saga SUCCEEDED.
        DeleteTreeSagaStep next = nextPendingStep(steps, current.sequenceNo());
        if (current.stepCode().equals("FINALIZE_TREE_DELETION")) {
            finalizeTree(state, now);
            state.transitionTo(DeleteTreeSagaState.State.SUCCEEDED, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            return;
        }
        if (next == null) {
            // Last participant just acked; move into FINALIZING and dispatch
            // the owner-local finalize step.
            state.transitionTo(DeleteTreeSagaState.State.FINALIZING, now);
            sagaRepo.saveState(state);
            finalizeTree(state, now);
            state.transitionTo(DeleteTreeSagaState.State.SUCCEEDED, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            return;
        }

        gateway.stageFirstStep(state, next);
        sagaRepo.saveState(state);
        gateway.stageOperationStateChanged(state);
    }

    private boolean satisfiesBarrier(DeleteTreeSagaState state, DeleteTreeSagaReplyCommand cmd) {
        return cmd.appliedAggregateVersion() >= state.targetAggregateVersion()
                && cmd.appliedEpoch() >= state.targetEpoch();
    }

    private static DeleteTreeSagaStep nextPendingStep(List<DeleteTreeSagaStep> steps, int currentSeq) {
        return steps.stream()
                .filter(s -> s.sequenceNo() > currentSeq)
                .filter(s -> s.state() == DeleteTreeSagaStep.State.PENDING)
                .findFirst()
                .orElse(null);
    }

    private void compensatePreviousSteps(DeleteTreeSagaState state,
                                         List<DeleteTreeSagaStep> steps,
                                         DeleteTreeSagaStep failedStep) {
        for (int i = failedStep.sequenceNo() - 1; i >= 1; i--) {
            int seq = i;
            steps.stream().filter(s -> s.sequenceNo() == seq).findFirst()
                    .ifPresent(prev -> {
                        if (prev.compensatable() && prev.state() == DeleteTreeSagaStep.State.ACK) {
                            gateway.stageCompensation(state, prev);
                        }
                    });
        }
    }

    private void finalizeTree(DeleteTreeSagaState state, Instant now) {
        Tree tree = treeRepo.findTree(state.treeId())
                .orElseThrow(() -> new TreeNotFoundException("Tree " + state.treeId() + " not found"));
        if (tree.isTombstoned()) return;
        tree.tombstone(tree.version());
        treeRepo.updateTree(tree);
        publisher.publishTreeEvent(new TreeAdvancedRevision(
                tree.id(), tree.revision(), tree.epoch(),
                state.operationId(), "delete-tree-saga:finalize", now));
    }

    public interface Clock { Instant now(); }
}