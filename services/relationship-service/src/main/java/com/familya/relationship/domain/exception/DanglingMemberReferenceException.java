package com.familya.relationship.domain.exception;

/**
 * Ngoại lệ nghiệp vụ được ném ra khi một lệnh tạo quan hệ tham chiếu tới một
 * thành viên không tồn tại hoặc đã bị xóa mềm (dangling reference).
 * <p>
 * Đây là cơ chế bảo vệ tính toàn vẹn tham chiếu xuyên service: Relationship
 * service chỉ lưu trữ các opaque ID do Member service phát ra. Trước khi tạo
 * bất kỳ cạnh nào, use case {@code CreateRelationshipUseCase} phải kiểm tra cả
 * hai đầu {@code fromMemberId} và {@code toMemberId} đều "khả dụng" trong
 * projection {@code MemberExistenceProjection}. Nếu một trong hai đầu không có
 * hoặc đã bị tombstone thì lệnh sẽ bị từ chối với ngoại lệ này.
 * </p>
 *
 * <p>
 * Ngoại lệ này thuộc nhóm <b>domain exception</b> - nghĩa là nó đại diện cho
 * một vi phạm bất biến (invariant) trong miền nghiệp vụ, không phải lỗi hệ
 * thống. Các tầng adapter (REST/Kafka) sẽ ánh xạ ngoại lệ này sang mã HTTP
 * 422 (Unprocessable Entity) hoặc tương đương.
 * </p>
 */
public class DanglingMemberReferenceException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả chi tiết.
     *
     * @param message mô tả lý do tham chiếu bị treo (ví dụ: ID thành viên,
     *               trạng thái hiện tại của projection)
     */
    public DanglingMemberReferenceException(String message) { super(message); }
}