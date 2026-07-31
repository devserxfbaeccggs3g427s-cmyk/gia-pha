package com.familya.treeaccess.domain.exception;

/**
 * Ngoại lệ ném khi không tìm thấy cây theo {@code treeId} yêu cầu.
 */
public class TreeNotFoundException extends RuntimeException {
    /**
     * @param message thông điệp mô tả ngữ cảnh lỗi
     */
    public TreeNotFoundException(String message) { super(message); }
}