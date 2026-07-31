package com.familya.relationship.domain.exception;

/**
 * Ngoại lệ nghiệp vụ được ném ra khi một lệnh tạo quan hệ {@code PARENT_CHILD}
 * (cha-con) sẽ tạo thành vòng (cycle) trong cây gia phả.
 * <p>
 * Cây gia phả về bản chất là một DAG (đồ thị có hướng không chu trình) theo
 * quan hệ cha-con: mỗi thành viên có đúng một nhóm tổ tiên (ancestor set) hữu
 * hạn. Nếu thêm cạnh {@code parent -> child} mà {@code child} lại nằm trong
 * tập tổ tiên của {@code parent} thì đồ thị sẽ xuất hiện chu trình, vi phạm
 * cấu trúc cây và gây ra các vòng lặp vô hạn trong các thuật toán duyệt
 * (BFS/DFS) cũng như sai lệch về mặt ngữ nghĩa.
 * </p>
 *
 * <p>
 * Use case {@code CreateRelationshipUseCase} sử dụng
 * {@code GraphAlgorithms.assertNoCycle} để phát hiện vòng trước khi chèn cạnh
 * mới và ném ra ngoại lệ này nếu phát hiện chu trình tiềm ẩn.
 * </p>
 */
public class CycleDetectedException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả vòng bị phát hiện.
     *
     * @param message mô tả chi tiết vòng, ví dụ: các đỉnh tham gia vào chu trình
     */
    public CycleDetectedException(String message) { super(message); }
}