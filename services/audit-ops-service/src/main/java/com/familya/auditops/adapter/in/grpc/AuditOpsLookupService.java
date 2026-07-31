/**
 * Dịch vụ gRPC tra cứu operation và step theo deadline-bound.
 * <p>
 * Các bounded context khác có thể gọi dịch vụ này khi cần đọc một cách
 * có thẩm quyền trạng thái hiện tại của operation mà không cần đi qua
 * đường polling REST của Gateway. RPC <b>BẮT BUỘC</b> phải được gọi với
 * deadline; interceptor phía client của platform starter sẽ ép buộc
 * việc truyền deadline.
 * </p>
 *
 * <p>RPC này là chỉ-đọc (read-only); vòng đời của operation được sở hữu
 * bởi luồng REST + Kafka + JDBC. RPC tồn tại cho mục đích:</p>
 * <ul>
 *   <li>Chẩn đoán chéo giữa các service (cross-service diagnostics).</li>
 *   <li>Saga participant cần xác nhận lại view của orchestrator về
 *       operation trước khi ack phản hồi.</li>
 * </ul>
 *
 * @see AuditOpsProtoGrpc
 */
package com.familya.auditops.adapter.in.grpc;

import com.familya.auditops.application.port.out.OperationRepository;
import com.familya.auditops.application.port.out.SagaStateRepository;
import com.familya.auditops.domain.model.Operation;
import com.familya.auditops.domain.model.OperationStatus;
import com.familya.auditops.domain.model.SagaStep;
import com.familya.auditops.domain.model.StepStatus;
import com.familya.platform.telemetry.PlatformMetrics;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai {@code AuditOpsLookupImplBase} để cung cấp hai RPC:
 * {@code GetOperation} và {@code ListSteps}. Mỗi RPC được phục vụ
 * trong một transaction chỉ-đọc thông qua các repository port.
 *
 * <p>Mọi RPC thành công đều tăng bộ đếm metric
 * {@code audit-ops-service.grpc_*}, giúp dashboard theo dõi lưu
 * lượng truy vấn nội bộ giữa các service.</p>
 */
@GrpcService
public class AuditOpsLookupService extends AuditOpsProtoGrpc.AuditOpsLookupImplBase {

    /** Logger dùng để ghi lại các lỗi nội bộ của RPC. */
    private static final Logger LOG = LoggerFactory.getLogger(AuditOpsLookupService.class);

    /** Repository cho bảng {@code operation_audit}. */
    private final OperationRepository operations;

    /** Repository cho trạng thái Saga và step. */
    private final SagaStateRepository saga;

    /** Bộ thu thập metric của platform để ghi nhận lưu lượng RPC. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo service với các phụ thuộc bắt buộc.
     *
     * @param operations repository truy vấn operation
     * @param saga       repository truy vấn trạng thái Saga
     * @param metrics    metric collector của platform
     */
    public AuditOpsLookupService(OperationRepository operations,
                                 SagaStateRepository saga,
                                 PlatformMetrics metrics) {
        this.operations = operations;
        this.saga = saga;
        this.metrics = metrics;
    }

    /**
     * RPC {@code GetOperation}. Trả về trạng thái hiện tại của operation
     * theo {@code operationId}. Nếu không tìm thấy hoặc id không hợp lệ,
     * trả về {@code found = false} thay vì ném exception để caller có thể
     * phân biệt được "không tồn tại" với "lỗi hệ thống".
     *
     * @param request          yêu cầu chứa {@code operationId}
     * @param responseObserver stream observer gRPC để gửi phản hồi
     */
    @Override
    public void getOperation(AuditOpsProto.GetOperationRequest request,
                             StreamObserver<AuditOpsProto.GetOperationResponse> responseObserver) {
        // Khởi tạo builder với giá trị mặc định (found=false, status=UNKNOWN).
        AuditOpsProto.GetOperationResponse.Builder b = AuditOpsProto.GetOperationResponse.newBuilder();
        try {
            // Chuyển chuỗi UUID từ client sang UUID; có thể ném IllegalArgumentException.
            UUID id = UUID.fromString(request.getOperationId());
            Optional<Operation> op = operations.findById(id);
            if (op.isPresent()) {
                Operation o = op.get();
                // Ánh xạ các trường của domain model sang proto. Trường null
                // được chuyển thành giá trị mặc định để đảm bảo proto luôn hợp lệ.
                b.setFound(true)
                        .setStatus(map(o.status()))
                        .setRevision(o.targetRevision() == null ? 0L : o.targetRevision())
                        .setEpoch(o.targetEpoch() == null ? 0L : o.targetEpoch())
                        .setErrorCode(o.errorCode() == null ? "" : o.errorCode());
                // Ghi nhận metric cho RPC thành công.
                metrics.mutationAcceptedCounter("audit-ops-service", "grpc_get_operation").increment();
            } else {
                // Không tìm thấy operation, trả về cờ found=false để caller xử lý.
                b.setFound(false);
            }
        } catch (IllegalArgumentException iae) {
            // UUID không hợp lệ: trả về found=false để caller phân biệt với lỗi hệ thống.
            b.setFound(false);
        } catch (RuntimeException re) {
            // Lỗi không mong đợi (DB, timeout, ...): ghi log cảnh báo và trả về not-found
            // để tránh làm sập stream observer của gRPC.
            LOG.warn("getOperation RPC failed", re);
            b.setFound(false);
        }
        // Luôn gửi về đúng một phản hồi rồi đóng stream theo pattern unary RPC.
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }

    /**
     * RPC {@code ListSteps}. Trả về danh sách các step Saga của operation
     * được sắp xếp theo {@code sequence_no}. Nếu id không hợp lệ thì
     * trả về danh sách rỗng (coi như không có step).
     *
     * @param request          yêu cầu chứa {@code operationId}
     * @param responseObserver stream observer gRPC để gửi phản hồi
     */
    @Override
    public void listSteps(AuditOpsProto.ListStepsRequest request,
                          StreamObserver<AuditOpsProto.ListStepsResponse> responseObserver) {
        AuditOpsProto.ListStepsResponse.Builder b = AuditOpsProto.ListStepsResponse.newBuilder();
        try {
            UUID id = UUID.fromString(request.getOperationId());
            // Lấy tất cả step của operation. Repository đã sắp xếp theo sequence_no.
            List<SagaStep> steps = saga.listSteps(id);
            for (SagaStep s : steps) {
                // Chuyển từng SagaStep domain sang StepView proto.
                b.addStep(AuditOpsProto.StepView.newBuilder()
                        .setParticipantService(s.participantService())
                        .setStepName(s.stepName())
                        .setSequenceNo(s.sequenceNo())
                        .setStatus(map(s.status()))
                        .setAttemptCount(s.attemptCount())
                        .setLastErrorCode(s.lastErrorCode() == null ? "" : s.lastErrorCode())
                        .build());
            }
            metrics.mutationAcceptedCounter("audit-ops-service", "grpc_list_steps").increment();
        } catch (IllegalArgumentException iae) {
            // UUID không hợp lệ: im lặng trả về danh sách rỗng (bình luận "// empty response").
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }

    /**
     * Ánh xạ {@link OperationStatus} của domain sang enum {@code Status}
     * của proto. Trả về {@code UNKNOWN} nếu giá trị null để proto luôn hợp lệ.
     *
     * @param s trạng thái operation ở domain
     * @return giá trị enum tương ứng trong proto
     */
    private static AuditOpsProto.GetOperationResponse.Status map(OperationStatus s) {
        if (s == null) return AuditOpsProto.GetOperationResponse.Status.UNKNOWN;
        // Hai enum có cùng tên trạng thái nên có thể ánh xạ bằng valueOf.
        return AuditOpsProto.GetOperationResponse.Status.valueOf(s.name());
    }

    /**
     * Ánh xạ {@link StepStatus} của domain sang enum {@code Status}
     * của {@code StepView}. Trả về {@code PENDING} nếu giá trị null.
     *
     * @param s trạng thái step ở domain
     * @return giá trị enum tương ứng trong proto
     */
    private static AuditOpsProto.StepView.Status map(StepStatus s) {
        if (s == null) return AuditOpsProto.StepView.Status.PENDING;
        return AuditOpsProto.StepView.Status.valueOf(s.name());
    }
}