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

    /** Deadline mặc định cho mỗi thao tác Saga delete-tree (60 phút). */
    static final Duration DEFAULT_DEADLINE = Duration.ofMinutes(60);
    /** Số lần thử tối đa mặc định cho mỗi bước Saga. */
    static final int     DEFAULT_MAX_ATTEMPTS = 5;

    /** Kho lưu trữ Saga. */
    private final DeleteTreeSagaRepository sagaRepo;
    /** Gateway stage các sự kiện ra outbox. */
    private final DeleteTreeSagaGateway gateway;
    /** Kho lưu trữ cây. */
    private final TreeRepository treeRepo;
    /** Bộ publish sự kiện cây. */
    private final TreeEventPublisher publisher;
    /** Metric giám sát. */
    private final PlatformMetrics metrics;
    /** Đồng hồ tiêm được. */
    private final Clock clock;

    /**
     * Khởi tạo service điều phối Saga.
     *
     * @param sagaRepo  kho lưu trữ Saga
     * @param gateway   gateway outbox
     * @param treeRepo  kho lưu trữ cây
     * @param publisher bộ publish sự kiện
     * @param metrics   metric giám sát
     * @param clock     đồng hồ
     */
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

    /**
     * Khởi tạo Saga xóa cây:
     *
     * <ol>
     *   <li>Chặn sớm nếu cây không tồn tại hoặc đã TOMBSTONED; yêu cầu caller là owner/ADMIN.</li>
     *   <li>Tính {@code targetRevision} và {@code targetEpoch} = hiện tại + 1.</li>
     *   <li>Tạo trạng thái Saga PENDING với deadline mặc định (60 phút).</li>
     *   <li>Khởi tạo 9 bước deterministic: freeze (local), tombstone (local), 6 bước participant, finalize (local).</li>
     *   <li>Thực thi ngay 2 bước local (đóng băng) vì chúng không cần tham gia viên khác.</li>
     *   <li>Dispatch bước participant đầu tiên và chuyển trạng thái Saga qua FREEZING → TOMBSTONING → PURGING.</li>
     *   <li>Stage {@code OperationStarted} để các hệ thống giám sát nắm được.</li>
     * </ol>
     *
     * @param cmd lệnh khởi tạo Saga
     * @return mã thao tác Saga bền vững
     */
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

        // Barrier: phải đạt được revision/epoch này trước khi finalize.
        long targetRevision = tree.revision() + 1;
        long targetEpoch = tree.epoch() + 1;

        DeleteTreeSagaState state = new DeleteTreeSagaState(
                operationId, cmd.treeId(), cmd.actingUser(), correlationId,
                DeleteTreeSagaState.State.PENDING,
                targetRevision, targetEpoch,
                now.plus(DEFAULT_DEADLINE), now, null, now, null, null, null);

        // Thứ tự bước: đóng băng/tombstone cục bộ (1,2) → fan-out tới tham gia viên (3-8) → finalize cục bộ (9).
        // Bước 1/2/9 thuộc owner nên chạy nội bộ, không cần gửi Kafka.
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

        // Bước 1 (FREEZE_TREE) là owner-local: ACTIVE → FROZEN để chặn ghi ngay lập tức.
        // Tombstone được hoãn tới Bước 9 (FINALIZE_TREE_DELETION) để rollback có thể "phục hồi" trước rào chắn không thể đảo ngược.
        tree.freeze(cmd.expectedTreeVersion());
        treeRepo.updateTree(tree);
        DeleteTreeSagaStep freezeStep = steps.get(0);
        freezeStep.dispatch(now);
        freezeStep.ack(now, tree.revision(), tree.epoch());
        sagaRepo.updateStep(freezeStep);
        DeleteTreeSagaStep tombstoneStep = steps.get(1);
        tombstoneStep.dispatch(now);
        tombstoneStep.ack(now, tree.revision(), tree.epoch());
        sagaRepo.updateStep(tombstoneStep);
        publisher.publishTreeEvent(new TreeFrozen(tree.id(), tree.revision(), tree.epoch(), now));

        publisher.publishTreeEvent(new TreeAdvancedRevision(
                tree.id(), tree.revision(), tree.epoch(),
                operationId, "delete-tree-saga:freeze", now));

        gateway.stageOperationStarted(state);
        dispatchFirstParticipant(state, steps.get(2), now); // bước participant đầu tiên
        state.transitionTo(DeleteTreeSagaState.State.FREEZING, now);
        state.transitionTo(DeleteTreeSagaState.State.TOMBSTONING, now);
        state.transitionTo(DeleteTreeSagaState.State.PURGING, now);
        sagaRepo.saveState(state);

        LOG.info("Initiated delete-tree Saga operationId={} treeId={}", operationId, tree.id());
        return operationId;
    }

    /**
     * Dispatch tham gia viên đầu tiên: claim token trước khi stage message để
     * tránh hai worker cùng gửi một bước.
     *
     * @param state trạng thái Saga
     * @param step  bước participant đầu tiên
     * @param now   thời điểm hiện tại
     */
    private void dispatchFirstParticipant(DeleteTreeSagaState state, DeleteTreeSagaStep step, Instant now) {
        UUID token = UUID.randomUUID();
        Instant stepDeadline = now.plusSeconds(30);
        boolean claimed = sagaRepo.tryClaimDispatch(step.operationId(), step.sequenceNo(), token, now, stepDeadline);
        if (!claimed) {
            // Worker khác đã claim — bỏ qua để tránh dispatch trùng.
            LOG.warn("dispatchFirstParticipant claim conflict op={} seq={}; skipping publish",
                    step.operationId(), step.sequenceNo());
            return;
        }
        step.claimDispatch(token, now, stepDeadline);
        sagaRepo.updateStep(step);
        gateway.stageFirstStep(state, step);
    }

    /**
     * Bắt buộc caller là owner hoặc ADMIN đã cấp quyền.
     *
     * @param tree   cây đang xử lý
     * @param userId UUID người thực hiện
     * @throws ForbiddenException nếu user không phải owner và không có ADMIN
     */
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

    /**
     * Factory rút gọn cho {@link DeleteTreeSagaStep} ở trạng thái PENDING.
     *
     * @param operationId   mã thao tác Saga
     * @param seq           số thứ tự bước
     * @param code          mã bước
     * @param participant   tên service tham gia
     * @param required      bước có bắt buộc không
     * @param compensatable bước có bù được không
     * @return bước Saga mới
     */
    private static DeleteTreeSagaStep step(UUID operationId, int seq, String code,
                                           String participant, boolean required,
                                           boolean compensatable) {
        return new DeleteTreeSagaStep(operationId, seq, code, participant,
                required, compensatable,
                DeleteTreeSagaStep.State.PENDING, 0, DEFAULT_MAX_ATTEMPTS,
                null,
                null, null, null, null,
                null, null, null, null, null);
    }

    /** Interface đồng hồ cho service. */
    public interface Clock { Instant now(); }
}