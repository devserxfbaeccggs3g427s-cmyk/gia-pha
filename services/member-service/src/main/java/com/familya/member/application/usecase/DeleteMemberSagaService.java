package com.familya.member.application.usecase;

import com.familya.member.application.port.in.InitiateDeleteMemberCommand;
import com.familya.member.application.port.out.DeleteMemberSagaGateway;
import com.familya.member.application.port.out.DeleteMemberSagaRepository;
import com.familya.member.application.port.out.MemberEventPublisher;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.event.MemberTombstoned;
import com.familya.member.domain.exception.MemberNotFoundException;
import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;
import com.familya.member.domain.model.Member;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.OptimisticConcurrencyException;
import com.familya.platform.projection.AuthorizationProjection;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeleteMemberSagaService {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaService.class);

    static final Duration DEFAULT_DEADLINE = Duration.ofMinutes(15);
    static final int     DEFAULT_MAX_ATTEMPTS = 5;

    private final DeleteMemberSagaRepository sagaRepo;
    private final DeleteMemberSagaGateway gateway;
    private final MemberRepository memberRepo;
    private final MemberEventPublisher memberPublisher;
    private final AuthorizationProjection authz;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public DeleteMemberSagaService(DeleteMemberSagaRepository sagaRepo,
                                   DeleteMemberSagaGateway gateway,
                                   MemberRepository memberRepo,
                                   MemberEventPublisher memberPublisher,
                                   AuthorizationProjection authz,
                                   PlatformMetrics metrics,
                                   Clock clock) {
        this.sagaRepo = sagaRepo;
        this.gateway = gateway;
        this.memberRepo = memberRepo;
        this.memberPublisher = memberPublisher;
        this.authz = authz;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public UUID initiate(InitiateDeleteMemberCommand cmd) {
        metrics.mutationAccepted("member-service", "deleteMember");
        Member m = memberRepo.findById(cmd.memberId())
                .orElseThrow(() -> new MemberNotFoundException(
                        "Member " + cmd.memberId() + " not found"));
        if (!m.treeId().equals(cmd.treeId())) {
            throw new IllegalArgumentException(
                    "Member " + cmd.memberId() + " does not belong to tree " + cmd.treeId());
        }
        if (m.isTombstoned()) {
            throw new OptimisticConcurrencyException(
                    "Member " + cmd.memberId() + " is already tombstoned");
        }
        AuthorizationProjection.Decision<com.familya.member.domain.model.MemberAuthRow> decision =
                authz.authorize(m.treeId(), cmd.actingUser(), cmd.expectedTreeRevision(),
                        com.familya.member.domain.model.MemberAuthRow.class);
        if (!decision.isAllowed()) {
            throw new ForbiddenException(
                    "User " + cmd.actingUser() + " cannot edit tree " + m.treeId());
        }

        Instant now = clock.now();
        UUID operationId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        long targetRevision = cmd.expectedTreeRevision() + 1;
        long targetEpoch = cmd.expectedTreeEpoch() + 1;

        // Step 1 is owner-local: tombstone the member and emit the domain event
        // atomically in this same transaction so participants never observe an
        // un-tombstoned member.
        m.tombstone(cmd.expectedMemberVersion(), now);
        memberRepo.update(m);
        memberPublisher.publish(new MemberTombstoned(
                m.treeId(), m.id(), m.version(), targetEpoch, now));

        DeleteMemberSagaState state = new DeleteMemberSagaState(
                operationId, cmd.treeId(), cmd.memberId(),
                cmd.actingUser(), correlationId,
                DeleteMemberSagaState.State.DISPATCHED,
                targetRevision, targetEpoch,
                now.plus(DEFAULT_DEADLINE), now, null, now, null, null, null);

        // Step 1 is the owner-local tombstone (compensatable=false, no Kafka
        // command needed). Steps 2..5 are external participants.
        List<DeleteMemberSagaStep> steps = List.of(
                step(operationId, 1, "TOMBSTONE_MEMBER",          "member-service",        true, false, m.version(), targetEpoch),
                step(operationId, 2, "DISABLE_RELATIONSHIPS",     "relationship-service",  true, true,  null, null),
                step(operationId, 3, "DETACH_EVENT_REFERENCES",   "event-service",         true, true,  null, null),
                step(operationId, 4, "DETACH_MEDIA_REFERENCES",   "media-service",         true, true,  null, null),
                step(operationId, 5, "ADVANCE_DELETE_MEMBER_REV", "tree-access-service",   true, false, null, null));

        sagaRepo.saveState(state);
        sagaRepo.saveSteps(steps);

        // Mark step 1 ACK locally (owner-local) before dispatching step 2.
        DeleteMemberSagaStep first = steps.get(0);
        first.ack(now, m.version(), targetEpoch);
        sagaRepo.updateStep(first);

        gateway.stageOperationStarted(state);
        gateway.stageFirstStep(state, steps.get(1));
        gateway.stageOperationStateChanged(state);

        LOG.info("Initiated delete-member Saga operationId={} memberId={} treeId={}",
                operationId, cmd.memberId(), cmd.treeId());
        return operationId;
    }

    private static DeleteMemberSagaStep step(UUID operationId, int seq, String code,
                                              String participant, boolean required,
                                              boolean compensatable,
                                              Long appliedVersion, Long appliedEpoch) {
        DeleteMemberSagaStep s = new DeleteMemberSagaStep(operationId, seq, code, participant,
                required, compensatable,
                DeleteMemberSagaStep.State.PENDING, 0, DEFAULT_MAX_ATTEMPTS,
                null, null, null, null, null, null);
        if (appliedVersion != null && appliedEpoch != null) {
            s.ack(Instant.EPOCH, appliedVersion, appliedEpoch);
        }
        return s;
    }

    public interface Clock { Instant now(); }
}