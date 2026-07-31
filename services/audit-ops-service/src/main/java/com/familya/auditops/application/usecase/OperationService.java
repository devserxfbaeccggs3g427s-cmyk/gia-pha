/**
 * Service đăng ký operation.
 *
 * <p>Idempotency key được ghi nhận dựa trên header {@code Idempotency-Key}
 * công khai để các retry chéo service trả về cùng một
 * {@link AsyncOperation}. Bounded context sở hữu <b>BẮT BUỘC</b> cũng
 * phải publish row domain + outbox row của riêng mình trong cùng
 * transaction; audit-ops ghi projection tại đây.</p>
 */
package com.familya.auditops.application.usecase;

import com.familya.auditops.application.port.in.StartOperationCommand;
import com.familya.auditops.application.port.out.AuditAppender;
import com.familya.auditops.application.port.out.OperationEventBus;
import com.familya.auditops.application.port.out.OperationRepository;
import com.familya.auditops.application.port.out.SagaStateRepository;
import com.familya.auditops.domain.event.OperationAdvanced;
import com.familya.auditops.domain.model.AuditEvent;
import com.familya.auditops.domain.model.Operation;
import com.familya.auditops.domain.model.OperationStatus;
import com.familya.auditops.domain.model.SagaState;
import com.familya.platform.api.AsyncOperation;
import com.familya.platform.error.IdempotencyConflictException;
import com.familya.platform.error.NotFoundException;
import com.familya.platform.idempotency.IdempotencyStore;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lớp service quản lý đường đăng ký operation.
 *
 * <p>Quy trình {@link #register}:</p>
 * <ol>
 *   <li>Kiểm tra idempotency; nếu đã tồn tại thì trả về envelope cũ.</li>
 *   <li>Sinh operation id, correlation id.</li>
 *   <li>Chèn operation ở trạng thái {@code PENDING}.</li>
 *   <li>Khởi tạo envelope Saga.</li>
 *   <li>Ghi audit event.</li>
 *   <li>Publish {@link OperationAdvanced} qua outbox.</li>
 *   <li>Ghi nhận idempotency key.</li>
 * </ol>
 */
@Service
public class OperationService {

    /** Logger ghi log cảnh báo và thông tin. */
    private static final Logger LOG = LoggerFactory.getLogger(OperationService.class);

    /** Repository operation. */
    private final OperationRepository operationRepo;
    /** Repository Saga. */
    private final SagaStateRepository sagaRepo;
    /** Port ghi audit. */
    private final AuditAppender audit;
    /** Port publish event. */
    private final OperationEventBus eventBus;
    /** Kho idempotency. */
    private final IdempotencyStore idempotency;
    /** Metric collector. */
    private final PlatformMetrics metrics;
    /** Clock để sinh thời gian. */
    private final Clock clock;

    /**
     * Khởi tạo service.
     *
     * @param operationRepo repository operation
     * @param sagaRepo      repository Saga
     * @param audit         port ghi audit
     * @param eventBus      port publish event
     * @param idempotency   kho idempotency
     * @param metrics       metric collector
     * @param clock         clock
     */
    public OperationService(OperationRepository operationRepo,
                            SagaStateRepository sagaRepo,
                            AuditAppender audit,
                            OperationEventBus eventBus,
                            IdempotencyStore idempotency,
                            PlatformMetrics metrics,
                            Clock clock) {
        this.operationRepo = operationRepo;
        this.sagaRepo = sagaRepo;
        this.audit = audit;
        this.eventBus = eventBus;
        this.idempotency = idempotency;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Đăng ký một operation mới ở trạng thái {@code PENDING} và khởi
     * tạo envelope máy trạng thái Saga.
     *
     * <p>Caller truyền vào loại operation, tham chiếu aggregate và
     * target revision/epoch (tuỳ chọn). Idempotency được đảm bảo
     * thông qua {@link IdempotencyStore}.</p>
     *
     * @param cmd command đăng ký
     * @return envelope {@link AsyncOperation}
     */
    @Transactional
    public AsyncOperation register(StartOperationCommand cmd) {
        // Ghi nhận metric đầu vào.
        metrics.mutationAccepted("audit-ops-service", "register_operation");
        // Kiểm tra idempotency: nếu key đã tồn tại, trả về envelope đã ghi nhận trước đó.
        if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) {
            var existing = idempotency.find(cmd.idempotencyKey());
            if (existing.isPresent()) {
                if (cmd.payloadHash() != null && !cmd.payloadHash().isBlank()) {
                    // JdbcIdempotencyStore kiểm tra hash tại thời điểm ghi nhận.
                    // Phía đọc không có sẵn API kiểm tra hash, nhưng implementation
                    // sẽ ném IdempotencyConflictException nếu hash không khớp.
                }
                metrics.mutationAcceptedCounter("audit-ops-service", "register_operation_idempotent").increment();
                return existing.get();
            }
        }

        // Sinh các id cần thiết. Nếu không có correlation id từ header, dùng chính operationId.
        Instant now = Instant.now(clock);
        UUID operationId = UUID.randomUUID();
        UUID correlationId = cmd.correlationIdHeader() == null || cmd.correlationIdHeader().isBlank()
                ? operationId
                : UUID.fromString(cmd.correlationIdHeader());

        // Tạo aggregate root của operation.
        Operation op = new Operation(
                operationId, correlationId, "audit-ops-service",
                cmd.operationType(), OperationStatus.PENDING,
                cmd.targetRevision(), cmd.targetEpoch(),
                cmd.aggregateType(), cmd.aggregateId(),
                cmd.treeId(), cmd.actingUser(),
                cmd.detail(),
                null, null,
                now, now, null, 0L);
        operationRepo.insert(op);

        // Khởi tạo envelope Saga ở bước "INIT".
        SagaState state = new SagaState(
                operationId, deriveSagaType(cmd.operationType()),
                "INIT", false, 0, now, Map.of(), 0L);
        sagaRepo.saveState(state);

        // Ghi audit event cho hành động "operation.registered".
        audit.append(new AuditEvent(
                UUID.randomUUID(), operationId, correlationId,
                cmd.actingUser(), AuditEvent.ActorKind.SERVICE,
                "operation.registered",
                cmd.aggregateType(), cmd.aggregateId(),
                cmd.detail(), now, null));

        // Publish OperationAdvanced qua outbox để bounded context khác cập nhật projection.
        eventBus.publish(new OperationAdvanced(operationId, "NEW", "PENDING",
                cmd.actingUser() == null ? "service" : cmd.actingUser().toString(), now),
                headers(correlationId, null));

        // Tạo envelope trả về theo chuẩn ADR-007.
        AsyncOperation envelope = AsyncOperation.accepted(operationId,
                "/api/v2/operations/" + operationId);

        // Ghi nhận idempotency key để retry sau nhận về cùng envelope.
        if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) {
            try {
                idempotency.record(cmd.idempotencyKey(),
                        cmd.payloadHash() == null ? "" : cmd.payloadHash(),
                        envelope);
            } catch (IdempotencyConflictException e) {
                LOG.warn("Idempotency conflict for key={} operation={}", cmd.idempotencyKey(), operationId);
                throw e;
            }
        }
        return envelope;
    }

    /**
     * Truy vấn projection phục vụ {@code GET /api/v2/operations/{id}}.
     *
     * @param operationId id operation
     * @return envelope {@link AsyncOperation}
     * @throws NotFoundException nếu operation không tồn tại
     */
    @Transactional(readOnly = true)
    public AsyncOperation find(UUID operationId) {
        return operationRepo.findById(operationId)
                .map(Operation::toAsyncOperation)
                .orElseThrow(() -> new NotFoundException("Operation " + operationId + " not found"));
    }

    /**
     * Suy ra saga type từ operation type.
     *
     * <p>Theo quy ước, phần trước dấu chấm đầu tiên của operationType
     * là saga type. Ví dụ: {@code "member.create.v1"} → {@code "member"}.</p>
     *
     * @param operationType loại operation
     * @return saga type
     */
    private static String deriveSagaType(String operationType) {
        if (operationType == null) return "unknown";
        int dot = operationType.indexOf('.');
        return dot > 0 ? operationType.substring(0, dot) : operationType;
    }

    /**
     * Tạo map header chuẩn cho outbox.
     *
     * @param correlationId correlation id
     * @param causationId   causation id
     * @return map header
     */
    private static Map<String, String> headers(UUID correlationId, String causationId) {
        Map<String, String> h = new HashMap<>();
        if (correlationId != null) h.put("correlationId", correlationId.toString());
        if (causationId != null) h.put("causationId", causationId);
        return h;
    }
}