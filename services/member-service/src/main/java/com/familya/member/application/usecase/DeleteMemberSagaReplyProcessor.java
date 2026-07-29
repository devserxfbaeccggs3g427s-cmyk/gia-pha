package com.familya.member.application.usecase;

import com.familya.member.application.port.in.DeleteMemberSagaReplyCommand;
import com.familya.member.application.port.out.DeleteMemberSagaGateway;
import com.familya.member.application.port.out.DeleteMemberSagaRepository;
import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;
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
 * Consumes Saga participant replies for the delete-member Saga. Verifies
 * that the reply satisfies the per-step target aggregate version and epoch
 * (the barrier requirement), advances or compensates the Saga, and stages
 * the resulting OperationStateChanged event on the outbox.
 */
@Service
public class DeleteMemberSagaReplyProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaReplyProcessor.class);

    private final DeleteMemberSagaRepository sagaRepo;
    private final DeleteMemberSagaGateway gateway;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public DeleteMemberSagaReplyProcessor(DeleteMemberSagaRepository sagaRepo,
                                          DeleteMemberSagaGateway gateway,
                                          PlatformMetrics metrics,
                                          Clock clock) {
        this.sagaRepo = sagaRepo;
        this.gateway = gateway;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void process(DeleteMemberSagaReplyCommand cmd) {
        Optional<DeleteMemberSagaState> opt = sagaRepo.findState(cmd.operationId());
        if (opt.isEmpty()) {
            LOG.warn("Ignoring reply for unknown operationId={}", cmd.operationId());
            return;
        }
        DeleteMemberSagaState state = opt.get();
        if (state.state().isTerminal()) {
            LOG.info("Ignoring reply for terminal Saga operationId={} state={}", state.operationId(), state.state());
            return;
        }

        List<DeleteMemberSagaStep> steps = sagaRepo.listSteps(cmd.operationId());
        DeleteMemberSagaStep current = steps.stream()
                .filter(s -> s.stepCode().equals(cmd.stepCode()))
                .filter(s -> s.participantService().equals(cmd.participantService()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No step " + cmd.stepCode() + " for " + cmd.participantService()));

        Instant now = clock.now();
        metrics.mutationAccepted("member-service", "deleteMember.reply");

        if (cmd.failed()) {
            current.fail(cmd.failureCode(), cmd.failureMessage(), now);
            sagaRepo.updateStep(current);
            state.recordFailure(cmd.failureCode(), cmd.failureMessage(), now);
            state.transitionTo(DeleteMemberSagaState.State.COMPENSATING, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            compensatePreviousSteps(state, steps, current);
            return;
        }

        if (!satisfiesBarrier(state, cmd)) {
            current.fail("BARRIER_NOT_MET",
                    "applied aggregateVersion=" + cmd.appliedAggregateVersion()
                            + " epoch=" + cmd.appliedEpoch()
                            + " below target", now);
            sagaRepo.updateStep(current);
            state.recordFailure("BARRIER_NOT_MET",
                    "step " + current.stepCode() + " did not meet barrier", now);
            state.transitionTo(DeleteMemberSagaState.State.MANUAL_REVIEW, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            return;
        }

        current.ack(now, cmd.appliedAggregateVersion(), cmd.appliedEpoch());
        sagaRepo.updateStep(current);

        DeleteMemberSagaStep next = nextPendingStep(steps, current.sequenceNo());
        if (next == null) {
            state.transitionTo(DeleteMemberSagaState.State.SUCCEEDED, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            return;
        }

        gateway.stageFirstStep(state, next);
        sagaRepo.saveState(state);
        gateway.stageOperationStateChanged(state);
    }

    private boolean satisfiesBarrier(DeleteMemberSagaState state, DeleteMemberSagaReplyCommand cmd) {
        return cmd.appliedAggregateVersion() >= state.targetAggregateVersion()
                && cmd.appliedEpoch() >= state.targetEpoch();
    }

    private static DeleteMemberSagaStep nextPendingStep(List<DeleteMemberSagaStep> steps, int currentSeq) {
        return steps.stream()
                .filter(s -> s.sequenceNo() > currentSeq)
                .filter(s -> s.state() == DeleteMemberSagaStep.State.PENDING)
                .findFirst()
                .orElse(null);
    }

    private void compensatePreviousSteps(DeleteMemberSagaState state,
                                         List<DeleteMemberSagaStep> steps,
                                         DeleteMemberSagaStep failedStep) {
        for (int i = failedStep.sequenceNo() - 1; i >= 1; i--) {
            int seq = i;
            steps.stream().filter(s -> s.sequenceNo() == seq).findFirst()
                    .ifPresent(prev -> {
                        if (prev.compensatable() && prev.state() == DeleteMemberSagaStep.State.ACK) {
                            gateway.stageCompensation(state, prev);
                        }
                    });
        }
    }

    public interface Clock { Instant now(); }
}