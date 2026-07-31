package com.familya.member.domain.exception;

/**
 * Ngoại lệ nghiệp vụ được ném khi không tìm thấy thành viên theo mã id cung cấp.
 */
public class MemberNotFoundException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message mô tả chi tiết về thành viên không tìm thấy
     */
    public MemberNotFoundException(String message) { super(message); }
}