package com.familya.member.domain.exception;

/**
 * Ngoại lệ nghiệp vụ được ném khi cố thao tác trên một thành viên đã bị tombstone
 * (đã xóa mềm).
 */
public class MemberTombstonedException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message mô tả chi tiết về thành viên đã tombstone
     */
    public MemberTombstonedException(String message) { super(message); }
}