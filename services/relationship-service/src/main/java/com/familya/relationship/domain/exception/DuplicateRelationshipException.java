package com.familya.relationship.domain.exception;

/**
 * Ngoại lệ nghiệp vụ được ném ra khi cố gắng tạo một cạnh (quan hệ) đã tồn
 * tại trong cùng một cây gia phả.
 * <p>
 * Để đảm bảo tính xác định (deterministic) của đồ thị, hệ thống quy định mỗi
 * bộ bốn {@code (treeId, kind, fromMember, toMember)} chỉ tồn tại duy nhất
 * một cạnh đang sống. Khi use case {@code CreateRelationshipUseCase} phát hiện
 * cạnh đã tồn tại (nhờ {@code RelationshipRepository.existsEdge}) thì lệnh sẽ
 * bị từ chối với ngoại lệ này để tránh tạo trùng lặp dữ liệu.
 * </p>
 *
 * <p>
 * Lưu ý: ngoại lệ chỉ áp dụng cho các cạnh <b>đang sống</b>. Nếu một quan hệ
 * đã bị tombstone trước đó thì việc tạo lại cùng một bộ bốn là hoàn toàn hợp
 * lệ và sẽ không ném ra ngoại lệ này.
 * </p>
 */
public class DuplicateRelationshipException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message mô tả cạnh trùng lặp, ví dụ: loại quan hệ và các ID thành viên
     */
    public DuplicateRelationshipException(String message) { super(message); }
}