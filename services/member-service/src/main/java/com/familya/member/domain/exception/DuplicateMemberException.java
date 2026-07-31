package com.familya.member.domain.exception;

/**
 * Ngoại lệ nghiệp vụ được ném khi phát hiện thành viên trùng lặp theo canonical key
 * (cùng tên, họ và ngày sinh trong một cây).
 */
public class DuplicateMemberException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message mô tả chi tiết về thành viên trùng lặp
     */
    public DuplicateMemberException(String message) { super(message); }
}