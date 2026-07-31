/**
 * POJO tương đương với mã sinh ra từ protobuf cho dịch vụ gRPC
 * {@code AuditOpsLookup}. Lớp này phản chiếu cấu trúc của file
 * {@code contracts/grpc/auditops/audit_ops.proto}.
 *
 * <p>Trong pipeline CI, file này sẽ được thay thế bằng mã sinh ra
 * tự động từ protoc; bề mặt builder public vẫn giữ nguyên để
 * đảm bảo tính tương thích ngược cho phần triển khai
 * {@link AuditOpsLookupService}.</p>
 */
package com.familya.auditops.adapter.in.grpc;

/**
 * Lớp tiện ích chứa các kiểu message và builder phục vụ cho RPC
 * {@code AuditOpsLookup}. Lớp này không thể khởi tạo (private constructor).
 */
public final class AuditOpsProto {
    private AuditOpsProto() { }

    /**
     * Yêu cầu RPC {@code GetOperation}: chỉ chứa mã operation cần truy vấn.
     */
    public static final class GetOperationRequest {
        /** ID của operation (UUID dạng chuỗi). */
        private String operationId = "";

        /**
         * Lấy mã operation từ request.
         *
         * @return chuỗi UUID
         */
        public String getOperationId() { return operationId; }

        /**
         * Tạo builder mới cho request.
         *
         * @return builder rỗng
         */
        public static Builder newBuilder() { return new Builder(); }

        /**
         * Builder cho {@link GetOperationRequest}.
         */
        public static final class Builder {
            /** Thể hiện request đang được xây dựng. */
            private final GetOperationRequest r = new GetOperationRequest();

            /**
             * Thiết lập mã operation.
             *
             * @param v chuỗi UUID
             * @return builder hiện tại (fluent)
             */
            public Builder setOperationId(String v) { r.operationId = v; return this; }

            /**
             * Đóng gói thành request hoàn chỉnh.
             *
             * @return đối tượng request
             */
            public GetOperationRequest build() { return r; }
        }
    }

    /**
     * Phản hồi RPC {@code GetOperation}: chứa cờ tìm thấy, trạng thái
     * hiện tại, revision/epoch mục tiêu và mã lỗi (nếu có).
     */
    public static final class GetOperationResponse {
        /**
         * Enum trạng thái ở phía proto, phản chiếu {@code OperationStatus}
         * của domain nhưng đặt tên theo quy ước proto (UNKNOWN, RUNNING, ...).
         */
        public enum Status { UNKNOWN, RUNNING, SUCCEEDED, FAILED, COMPENSATED, MANUAL_REVIEW, PENDING }
        /** true nếu tìm thấy operation. */
        private boolean found;
        /** Trạng thái hiện tại của operation. */
        private Status status = Status.UNKNOWN;
        /** Revision mục tiêu đã đăng ký. */
        private long revision;
        /** Epoch mục tiêu đã đăng ký. */
        private long epoch;
        /** Mã lỗi nếu operation ở trạng thái lỗi. */
        private String errorCode = "";
        /** Mã trace liên quan tới operation (nếu có). */
        private String traceId = "";

        /** @return true nếu tìm thấy operation. */
        public boolean getFound() { return found; }
        /** @return trạng thái hiện tại. */
        public Status getStatus() { return status; }
        /** @return revision mục tiêu. */
        public long getRevision() { return revision; }
        /** @return epoch mục tiêu. */
        public long getEpoch() { return epoch; }
        /** @return mã lỗi (chuỗi rỗng nếu không có). */
        public String getErrorCode() { return errorCode; }
        /** @return mã trace (chuỗi rỗng nếu không có). */
        public String getTraceId() { return traceId; }

        /** @return builder mới cho response. */
        public static Builder newBuilder() { return new Builder(); }

        /**
         * Builder cho {@link GetOperationResponse}.
         */
        public static final class Builder {
            /** Thể hiện response đang được xây dựng. */
            private final GetOperationResponse r = new GetOperationResponse();

            /**
             * Thiết lập cờ tìm thấy.
             * @param v true nếu tìm thấy
             * @return builder hiện tại
             */
            public Builder setFound(boolean v) { r.found = v; return this; }

            /**
             * Thiết lập trạng thái; null sẽ được thay bằng {@code UNKNOWN}.
             * @param v trạng thái
             * @return builder hiện tại
             */
            public Builder setStatus(Status v) { r.status = v == null ? Status.UNKNOWN : v; return this; }

            /**
             * Thiết lập revision mục tiêu.
             * @param v giá trị revision
             * @return builder hiện tại
             */
            public Builder setRevision(long v) { r.revision = v; return this; }

            /**
             * Thiết lập epoch mục tiêu.
             * @param v giá trị epoch
             * @return builder hiện tại
             */
            public Builder setEpoch(long v) { r.epoch = v; return this; }

            /**
             * Thiết lập mã lỗi (null sẽ thành chuỗi rỗng).
             * @param v mã lỗi
             * @return builder hiện tại
             */
            public Builder setErrorCode(String v) { r.errorCode = v == null ? "" : v; return this; }

            /**
             * Thiết lập mã trace (null sẽ thành chuỗi rỗng).
             * @param v mã trace
             * @return builder hiện tại
             */
            public Builder setTraceId(String v) { r.traceId = v == null ? "" : v; return this; }

            /**
             * Đóng gói response hoàn chỉnh.
             * @return response
             */
            public GetOperationResponse build() { return r; }
        }
    }

    /**
     * Yêu cầu RPC {@code ListSteps}: chứa mã operation cần liệt kê các step.
     */
    public static final class ListStepsRequest {
        /** ID operation (UUID dạng chuỗi). */
        private String operationId = "";

        /** @return chuỗi UUID của operation. */
        public String getOperationId() { return operationId; }

        /** @return builder mới cho request. */
        public static Builder newBuilder() { return new Builder(); }

        /**
         * Builder cho {@link ListStepsRequest}.
         */
        public static final class Builder {
            /** Thể hiện request đang được xây dựng. */
            private final ListStepsRequest r = new ListStepsRequest();

            /**
             * Thiết lập mã operation.
             * @param v chuỗi UUID
             * @return builder hiện tại
             */
            public Builder setOperationId(String v) { r.operationId = v; return this; }

            /**
             * Đóng gói request hoàn chỉnh.
             * @return request
             */
            public ListStepsRequest build() { return r; }
        }
    }

    /**
     * View hiển thị một Saga step trong RPC {@code ListSteps}.
     */
    public static final class StepView {
        /**
         * Enum trạng thái step ở phía proto, phản chiếu {@code StepStatus}
         * của domain (PENDING, DISPATCHED, ACKED, ...).
         */
        public enum Status { PENDING, DISPATCHED, ACKED, FAILED, COMPENSATED, DEAD_LETTERED }
        /** Tên bounded context participant. */
        private String participantService = "";
        /** Tên step trong Saga. */
        private String stepName = "";
        /** Thứ tự step trong Saga. */
        private int sequenceNo;
        /** Trạng thái step. */
        private Status status = Status.PENDING;
        /** Số lần step đã được thử. */
        private long attemptCount;
        /** Mã lỗi của lần thử gần nhất (nếu có). */
        private String lastErrorCode = "";

        /** @return tên participant service. */
        public String getParticipantService() { return participantService; }
        /** @return tên step. */
        public String getStepName() { return stepName; }
        /** @return thứ tự sequence. */
        public int getSequenceNo() { return sequenceNo; }
        /** @return trạng thái step. */
        public Status getStatus() { return status; }
        /** @return số lần đã thử. */
        public long getAttemptCount() { return attemptCount; }
        /** @return mã lỗi lần gần nhất. */
        public String getLastErrorCode() { return lastErrorCode; }

        /** @return builder mới cho {@link StepView}. */
        public static Builder newBuilder() { return new Builder(); }

        /**
         * Builder cho {@link StepView}.
         */
        public static final class Builder {
            /** Thể hiện view đang được xây dựng. */
            private final StepView r = new StepView();

            /** @param v tên participant. @return builder hiện tại. */
            public Builder setParticipantService(String v) { r.participantService = v == null ? "" : v; return this; }

            /** @param v tên step. @return builder hiện tại. */
            public Builder setStepName(String v) { r.stepName = v == null ? "" : v; return this; }

            /** @param v thứ tự. @return builder hiện tại. */
            public Builder setSequenceNo(int v) { r.sequenceNo = v; return this; }

            /** @param v trạng thái. @return builder hiện tại. */
            public Builder setStatus(Status v) { r.status = v == null ? Status.PENDING : v; return this; }

            /** @param v số lần thử. @return builder hiện tại. */
            public Builder setAttemptCount(long v) { r.attemptCount = v; return this; }

            /** @param v mã lỗi. @return builder hiện tại. */
            public Builder setLastErrorCode(String v) { r.lastErrorCode = v == null ? "" : v; return this; }

            /** @return view hoàn chỉnh. */
            public StepView build() { return r; }
        }
    }

    /**
     * Phản hồi RPC {@code ListSteps}: chứa danh sách các {@link StepView}.
     */
    public static final class ListStepsResponse {
        /** Danh sách step view (sử dụng {@code ArrayList} để có thể thêm tuần tự). */
        private final java.util.List<StepView> steps = new java.util.ArrayList<>();
        /** @return danh sách step (không phải unmodifiable). */
        public java.util.List<StepView> getStepsList() { return steps; }
        /** @return builder mới cho response. */
        public static Builder newBuilder() { return new Builder(); }

        /**
         * Builder cho {@link ListStepsResponse}.
         */
        public static final class Builder {
            /** Thể hiện response đang được xây dựng. */
            private final ListStepsResponse r = new ListStepsResponse();

            /**
             * Thêm một step view vào danh sách.
             * @param v step view cần thêm
             * @return builder hiện tại
             */
            public Builder addStep(StepView v) { r.steps.add(v); return this; }

            /** @return response hoàn chỉnh. */
            public ListStepsResponse build() { return r; }
        }
    }
}