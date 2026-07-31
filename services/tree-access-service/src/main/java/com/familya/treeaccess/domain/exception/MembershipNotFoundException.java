package com.familya.treeaccess.domain.exception;

/**
 * Ngoại lệ ném khi không tìm thấy dòng membership cho người dùng trên cây.
 */
public class MembershipNotFoundException extends RuntimeException {
    /**
     * @param message thông điệp mô tả ngữ cảnh lỗi
     */
    public MembershipNotFoundException(String message) { super(message); }
}