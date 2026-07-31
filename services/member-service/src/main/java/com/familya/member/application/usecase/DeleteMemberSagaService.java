package com.familya.member.application.usecase;

import com.familya.member.adapter.out.events.DeleteMemberSagaCommandContext;
import com.familya.member.adapter.out.persistence.JdbcOperationAuditWriter;
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
import com.familya.platform.api.AsyncOperation;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.OptimisticConcurrencyException;
import com.familya.platform.idempotency.IdempotencyStore;
import com.familya.platform.idempotency.SagaIdempotency;
import com.familya.platform.projection.AuthorizationProjection;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dịch vụ khởi tạo Saga xóa thành viên. Thực hiện:
 * <ol>
 *   <li>Reserve-or-replay idempotency (theo owner + command + principal + Idempotency-Key).</li>
 *   <li>Kiểm tra quyền và trạng thái thành viên.</li>
 *   <li>Tombstone thành viên cục bộ (bước 1, owner-local).</li>
 *   <li>Khởi tạo state Saga, ghi {@code operation_audit} (cùng transaction).</li>
 *   <li>Stage OperationStarted, dispatch bước participant đầu tiên, stage OperationStateChanged.</li>
 *   <li>Commit idempotency record.</li>
 * </ol>
 */
@Service
public class DeleteMemberSagaService {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaService.class);

    /** Deadline mặc định cho toàn bộ Saga (15 phút). */
    static final Duration DEFAULT_DEADLINE = Duration.ofMinutes(15);
    /** Số lần thử tối đa cho mỗi bước. */
    static final int     DEFAULT_MAX_ATTEMPTS = 5;

    private final DeleteMemberSagaRepository sagaRepo;
    private final DeleteMemberSagaGateway gateway;
    private final MemberRepository memberRepo;
    private final MemberEventPublisher memberPublisher;
    private final AuthorizationProjection authz;
    private final PlatformMetrics metrics;
    private final Clock clock;
    private final IdempotencyStore idempotency;
    private final JdbcOperationAuditWriter operationAudit;
    private final DeleteMemberSagaCommandContext commandContext;

    public DeleteMemberSagaService(DeleteMemberSagaRepository sagaRepo,
                                   DeleteMemberSagaGateway gateway,
                                   MemberRepository memberRepo,
                                   MemberEventPublisher memberPublisher,
                                   AuthorizationProjection authz,
                                   PlatformMetrics metrics,
                                   Clock clock,
                                   IdempotencyStore idempotency,
                                   JdbcOperationAuditWriter operationAudit,
                                   DeleteMemberSagaCommandContext commandContext) {
        this.sagaRepo = sagaRepo;
        this.gateway = gateway;
        this.memberRepo = memberRepo;
        this.memberPublisher = memberPublisher;
        this.authz = authz;
        this.metrics = metrics;
        this.clock = clock;
        this.idempotency = idempotency;
        this.operationAudit = operationAudit;
        this.commandContext = commandContext;
    }

    /**
     * Khởi tạo Saga xóa thành viên, trả về {@code operationId} để client theo dõi.
     */
    @Transactional
    public AsyncOperation initiate(InitiateDeleteMemberCommand cmd) {
        metrics.mutationAccepted("member-service", "deleteMember");

        String idempotencyKey = cmd.idempotencyKey();
        String payloadHash = SagaIdempotency.canonicalHash(
                "delete-member", cmd.actingUser(), cmd.treeId().toString(),
                cmd.memberId() + "|" + cmd.expectedTreeRevision() + "|" + cmd.expectedTreeEpoch());
        Optional<AsyncOperation> existing = SagaIdempotency.reserve(idempotency, idempotencyKey);
        if (existing.isPresent()) {
            metrics.mutationAcceptedCounter("member-service", "delete_member_idempotent").increment();
            return existing.get();
        }

        // Tra cứu thành viên, báo lỗi nếu không tồn tại
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

        List<DeleteMemberSagaStep> steps = List.of(
                step(operationId, 1, "TOMBSTONE_MEMBER",          "member-service",        true, false, m.version(), targetEpoch),
                step(operationId, 2, "DISABLE_RELATIONSHIPS",     "relationship-service",  true, true,  null, null),
                step(operationId, 3, "DETACH_EVENT_REFERENCES",   "event-service",         true, true,  null, null),
                step(operationId, 4, "DETACH_MEDIA_REFERENCES",   "media-service",         true, true,  null, null),
                step(operationId, 5, "ADVANCE_DELETE_MEMBER_REV", "tree-access-service",   true, false, null, null));

        sagaRepo.saveState(state);
        sagaRepo.saveSteps(steps);

        DeleteMemberSagaStep first = steps.get(0);
        first.dispatch(now);
        first.ack(now, m.version(), targetEpoch);
        sagaRepo.updateStep(first);

        // Ghi operation_audit trong cùng transaction để OperationProjectionAdapter
        // có thể trả envelope ngay khi client poll.
        operationAudit.recordInitiated(operationId, correlationId, cmd.treeId(),
                cmd.actingUser(), cmd.memberId(), "delete-member",
                targetRevision, targetEpoch, now);

        // Set context cho causation/traceparent chain; clear khi Saga kết thúc
        // (lifecycle xử lý ở các lớp sâu hơn).
        commandContext.startSaga(cmd.traceparent());

        gateway.stageOperationStarted(state);
        dispatchFirstParticipant(state, steps.get(1), now);
        gateway.stageOperationStateChanged(state);

        AsyncOperation envelope = AsyncOperation.accepted(operationId, "/api/v2/operations/" + operationId);
        SagaIdempotency.commit(idempotency, idempotencyKey, payloadHash, envelope);

        LOG.info("Initiated delete-member Saga operationId={} memberId={} treeId={}",
                operationId, cmd.memberId(), cmd.treeId());
        return envelope;
    }

    /**
     * Dispatch participant đầu tiên của Saga: giành quyền dispatch và stage lệnh lên outbox.
     * Nếu không giành được (xung đột), bỏ qua để tránh double-publish.
     */
    private void dispatchFirstParticipant(DeleteMemberSagaState state, DeleteMemberSagaStep step, Instant now) {
        UUID token = UUID.randomUUID();
        Instant stepDeadline = now.plusSeconds(30);
        boolean claimed = sagaRepo.tryClaimDispatch(step.operationId(), step.sequenceNo(), token, now, stepDeadline);
        if (!claimed) {
            LOG.warn("dispatchFirstParticipant claim conflict op={} seq={}; skipping publish",
                    step.operationId(), step.sequenceNo());
            return;
        }
        step.claimDispatch(token, now, stepDeadline);
        sagaRepo.updateStep(step);
        gateway.stageFirstStep(state, step);
    }

    /**
     * Hàm tiện ích tạo bước Saga. Nếu {@code appliedVersion} và {@code appliedEpoch} được cung cấp
     * (dành cho bước đã ACK sẵn như bước 1 owner-local), bước sẽ được đánh dấu ACK ngay tại EPOCH.
     */
    private static DeleteMemberSagaStep step(UUID operationId, int seq, String code,
                                              String participant, boolean required,
                                              boolean compensatable,
                                              Long appliedVersion, Long appliedEpoch) {
        DeleteMemberSagaStep s = new DeleteMemberSagaStep(operationId, seq, code, participant,
                required, compensatable,
                DeleteMemberSagaStep.State.PENDING, 0, DEFAULT_MAX_ATTEMPTS,
                null,
                null, null, null, null,
                null, null, null, null, null);
        if (appliedVersion != null && appliedEpoch != null) {
            s.ack(Instant.EPOCH, appliedVersion, appliedEpoch);
        }
        return s;
    }

    /** Đồng hồ tiêm được cho use case. */
    public interface Clock { Instant now(); }
}