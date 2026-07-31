package com.familya.auditops.adapter.in.grpc;

import io.grpc.stub.StreamObserver;

/**
 * Stand-in for the generated gRPC service base. Mirrors the
 * {@code contracts/grpc/auditops/audit_ops.proto} generated
 * {@code AuditOpsLookupImplBase}. The CI pipeline replaces this
 * file with generated source; the public method signatures are
 * identical.
 */
public final class AuditOpsProtoGrpc {
    private AuditOpsProtoGrpc() { }

    public static abstract class AuditOpsLookupImplBase implements io.grpc.BindableService {
        public void getOperation(AuditOpsProto.GetOperationRequest request,
                                 StreamObserver<AuditOpsProto.GetOperationResponse> responseObserver) {
            throw new UnsupportedOperationException("getOperation() must be overridden");
        }

        public void listSteps(AuditOpsProto.ListStepsRequest request,
                              StreamObserver<AuditOpsProto.ListStepsResponse> responseObserver) {
            throw new UnsupportedOperationException("listSteps() must be overridden");
        }

        @Override
        public io.grpc.ServerServiceDefinition bindService() {
            return io.grpc.ServerServiceDefinition.builder("familya.auditops.v1.AuditOpsLookup").build();
        }
    }
}