package com.familya.auditops.adapter.in.grpc;

/**
 * Generated-by-protobuf-equivalent POJO for the
 * {@code AuditOpsLookup} gRPC service. Mirrors the
 * {@code contracts/grpc/auditops/audit_ops.proto} contract. The CI
 * pipeline replaces this file with the generated source; the public
 * builder surface is identical.
 */
public final class AuditOpsProto {
    private AuditOpsProto() { }

    public static final class GetOperationRequest {
        private String operationId = "";
        public String getOperationId() { return operationId; }
        public static Builder newBuilder() { return new Builder(); }
        public static final class Builder {
            private final GetOperationRequest r = new GetOperationRequest();
            public Builder setOperationId(String v) { r.operationId = v; return this; }
            public GetOperationRequest build() { return r; }
        }
    }

    public static final class GetOperationResponse {
        public enum Status { UNKNOWN, RUNNING, SUCCEEDED, FAILED, COMPENSATED, MANUAL_REVIEW, PENDING }
        private boolean found;
        private Status status = Status.UNKNOWN;
        private long revision;
        private long epoch;
        private String errorCode = "";
        private String traceId = "";

        public boolean getFound() { return found; }
        public Status getStatus() { return status; }
        public long getRevision() { return revision; }
        public long getEpoch() { return epoch; }
        public String getErrorCode() { return errorCode; }
        public String getTraceId() { return traceId; }

        public static Builder newBuilder() { return new Builder(); }
        public static final class Builder {
            private final GetOperationResponse r = new GetOperationResponse();
            public Builder setFound(boolean v) { r.found = v; return this; }
            public Builder setStatus(Status v) { r.status = v == null ? Status.UNKNOWN : v; return this; }
            public Builder setRevision(long v) { r.revision = v; return this; }
            public Builder setEpoch(long v) { r.epoch = v; return this; }
            public Builder setErrorCode(String v) { r.errorCode = v == null ? "" : v; return this; }
            public Builder setTraceId(String v) { r.traceId = v == null ? "" : v; return this; }
            public GetOperationResponse build() { return r; }
        }
    }

    public static final class ListStepsRequest {
        private String operationId = "";
        public String getOperationId() { return operationId; }
        public static Builder newBuilder() { return new Builder(); }
        public static final class Builder {
            private final ListStepsRequest r = new ListStepsRequest();
            public Builder setOperationId(String v) { r.operationId = v; return this; }
            public ListStepsRequest build() { return r; }
        }
    }

    public static final class StepView {
        public enum Status { PENDING, DISPATCHED, ACKED, FAILED, COMPENSATED, DEAD_LETTERED }
        private String participantService = "";
        private String stepName = "";
        private int sequenceNo;
        private Status status = Status.PENDING;
        private long attemptCount;
        private String lastErrorCode = "";

        public String getParticipantService() { return participantService; }
        public String getStepName() { return stepName; }
        public int getSequenceNo() { return sequenceNo; }
        public Status getStatus() { return status; }
        public long getAttemptCount() { return attemptCount; }
        public String getLastErrorCode() { return lastErrorCode; }

        public static Builder newBuilder() { return new Builder(); }
        public static final class Builder {
            private final StepView r = new StepView();
            public Builder setParticipantService(String v) { r.participantService = v == null ? "" : v; return this; }
            public Builder setStepName(String v) { r.stepName = v == null ? "" : v; return this; }
            public Builder setSequenceNo(int v) { r.sequenceNo = v; return this; }
            public Builder setStatus(Status v) { r.status = v == null ? Status.PENDING : v; return this; }
            public Builder setAttemptCount(long v) { r.attemptCount = v; return this; }
            public Builder setLastErrorCode(String v) { r.lastErrorCode = v == null ? "" : v; return this; }
            public StepView build() { return r; }
        }
    }

    public static final class ListStepsResponse {
        private final java.util.List<StepView> steps = new java.util.ArrayList<>();
        public java.util.List<StepView> getStepsList() { return steps; }
        public static Builder newBuilder() { return new Builder(); }
        public static final class Builder {
            private final ListStepsResponse r = new ListStepsResponse();
            public Builder addStep(StepView v) { r.steps.add(v); return this; }
            public ListStepsResponse build() { return r; }
        }
    }
}