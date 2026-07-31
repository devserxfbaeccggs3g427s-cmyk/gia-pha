/**
 * Rào chuyển trạng thái Saga.
 *
 * <p>Các transition được mã hoá ở đây thực thi đường đi chuẩn đã mô
 * tả trong Javadoc của {@link OperationStatus}.</p>
 *
 * <p>Lớp này cố ý độc lập với framework: không phụ thuộc Spring,
 * JDBC hay Kafka. Orchestrator tham khảo nó trước mỗi lần ghi để
 * đảm bảo transition bất hợp pháp không bao giờ chạm tới database.
 * Transition compensation đưa operation về
 * {@link OperationStatus#COMPENSATED}; các vi phạm ranh giới không
 * thể đảo được chuyển sang {@link OperationStatus#MANUAL_REVIEW}.</p>
 */
package com.familya.auditops.domain.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lớp tiện ích cung cấp các phương thức kiểm tra và yêu cầu
 * transition Saga hợp lệ.
 */
public final class SagaTransitions {

    /**
     * Bảng ánh xạ từ trạng thái hiện tại sang tập các trạng thái đích
     * được phép. Cấu trúc này mã hoá các quy tắc nghiệp vụ trong
     * ADR-007.
     */
    private static final Map<OperationStatus, Set<OperationStatus>> ALLOWED = Map.of(
            OperationStatus.PENDING, EnumSet.of(OperationStatus.RUNNING, OperationStatus.FAILED, OperationStatus.MANUAL_REVIEW),
            OperationStatus.RUNNING, EnumSet.of(OperationStatus.SUCCEEDED, OperationStatus.FAILED, OperationStatus.COMPENSATING, OperationStatus.MANUAL_REVIEW),
            OperationStatus.FAILED, EnumSet.of(OperationStatus.COMPENSATING, OperationStatus.MANUAL_REVIEW),
            OperationStatus.COMPENSATING, EnumSet.of(OperationStatus.COMPENSATED, OperationStatus.MANUAL_REVIEW),
            OperationStatus.SUCCEEDED, EnumSet.noneOf(OperationStatus.class),
            OperationStatus.COMPENSATED, EnumSet.noneOf(OperationStatus.class),
            OperationStatus.MANUAL_REVIEW, EnumSet.of(OperationStatus.RUNNING, OperationStatus.COMPENSATING, OperationStatus.COMPENSATED)
    );

    /** Không cho phép khởi tạo lớp tiện ích. */
    private SagaTransitions() { }

    /**
     * Kiểm tra transition có được phép hay không.
     *
     * <p>Transition {@code from → from} (giữ nguyên) luôn bị coi là
     * không được phép vì nó không phải transition thực sự.</p>
     *
     * @param from trạng thái hiện tại
     * @param to   trạng thái đích
     * @return true nếu transition hợp lệ
     */
    public static boolean isAllowed(OperationStatus from, OperationStatus to) {
        if (from == to) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(OperationStatus.class)).contains(to);
    }

    /**
     * Yêu cầu transition hợp lệ; ném {@link com.familya.auditops.domain.exception.SagaConflictException}
     * nếu không hợp lệ.
     *
     * @param from trạng thái hiện tại
     * @param to   trạng thái đích
     * @throws com.familya.auditops.domain.exception.SagaConflictException nếu transition bất hợp pháp
     */
    public static void requireAllowed(OperationStatus from, OperationStatus to) {
        if (!isAllowed(from, to)) {
            throw new com.familya.auditops.domain.exception.SagaConflictException(
                    "Illegal Saga transition " + from + " -> " + to);
        }
    }
}