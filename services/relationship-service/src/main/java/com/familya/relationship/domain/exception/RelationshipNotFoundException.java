package com.familya.relationship.domain.exception;

/**
 * Ngoại lệ nghiệp vụ được ném ra khi một lệnh truy vấn hoặc thao tác yêu cầu
 * truy xuất một quan hệ nhưng không tìm thấy trong cơ sở dữ liệu.
 * <p>
 * Trường hợp sử dụng phổ biến nhất là use case {@code TombstoneRelationshipUseCase}
 * khi nhận được {@code relationshipId} không tồn tại (có thể do ID sai, do quan
 * hệ đã bị xóa vĩnh viễn, hoặc do gọi nhầm vào quan hệ của cây khác). Ngoại lệ
 * này đảm bảo use case phản hồi nhất quán với adapter REST (HTTP 404) và
 * giúp client phân biệt "không tìm thấy" với các lỗi khác (ví dụ: cấm truy
 * cập, xung đột phiên bản).
 * </p>
 */
public class RelationshipNotFoundException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message mô tả ID quan hệ bị thiếu hoặc ngữ cảnh truy vấn
     */
    public RelationshipNotFoundException(String message) { super(message); }
}