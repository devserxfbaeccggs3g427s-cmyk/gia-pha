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
 * Deadline-bound gRPC service for operation and step lookup. Other
 * services may call this when they need an authoritative read of an
 * operation's current state without going through the Gateway's REST
 * polling path. The RPC MUST be invoked with a deadline; the platform
 * starter's client interceptor enforces deadline propagation.
 *
 * <p>This RPC is read-only; the operation lifecycle is owned by the
 * REST + Kafka + JDBC paths. The RPC exists for cross-service
 * diagnostics and for the Saga participant when it needs to confirm
 * the orchestrator's view of the operation before acking.</p>
 */
@GrpcService
public class AuditOpsLookupService extends AuditOpsProtoGrpc.AuditOpsLookupImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(AuditOpsLookupService.class);

    private final OperationRepository operations;
    private final SagaStateRepository saga;
    private final PlatformMetrics metrics;

    public AuditOpsLookupService(OperationRepository operations,
                                 SagaStateRepository saga,
                                 PlatformMetrics metrics) {
        this.operations = operations;
        this.saga = saga;
        this.metrics = metrics;
    }

    @Override
    public void getOperation(AuditOpsProto.GetOperationRequest request,
                             StreamObserver<AuditOpsProto.GetOperationResponse> responseObserver) {
        AuditOpsProto.GetOperationResponse.Builder b = AuditOpsProto.GetOperationResponse.newBuilder();
        try {
            UUID id = UUID.fromString(request.getOperationId());
            Optional<Operation> op = operations.findById(id);
            if (op.isPresent()) {
                Operation o = op.get();
                b.setFound(true)
                        .setStatus(map(o.status()))
                        .setRevision(o.targetRevision() == null ? 0L : o.targetRevision())
                        .setEpoch(o.targetEpoch() == null ? 0L : o.targetEpoch())
                        .setErrorCode(o.errorCode() == null ? "" : o.errorCode());
                metrics.mutationAcceptedCounter("audit-ops-service", "grpc_get_operation").increment();
            } else {
                b.setFound(false);
            }
        } catch (IllegalArgumentException iae) {
            b.setFound(false);
        } catch (RuntimeException re) {
            LOG.warn("getOperation RPC failed", re);
            b.setFound(false);
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }

    @Override
    public void listSteps(AuditOpsProto.ListStepsRequest request,
                          StreamObserver<AuditOpsProto.ListStepsResponse> responseObserver) {
        AuditOpsProto.ListStepsResponse.Builder b = AuditOpsProto.ListStepsResponse.newBuilder();
        try {
            UUID id = UUID.fromString(request.getOperationId());
            List<SagaStep> steps = saga.listSteps(id);
            for (SagaStep s : steps) {
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
            // empty response
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }

    private static AuditOpsProto.GetOperationResponse.Status map(OperationStatus s) {
        if (s == null) return AuditOpsProto.GetOperationResponse.Status.UNKNOWN;
        return AuditOpsProto.GetOperationResponse.Status.valueOf(s.name());
    }

    private static AuditOpsProto.StepView.Status map(StepStatus s) {
        if (s == null) return AuditOpsProto.StepView.Status.PENDING;
        return AuditOpsProto.StepView.Status.valueOf(s.name());
    }
}