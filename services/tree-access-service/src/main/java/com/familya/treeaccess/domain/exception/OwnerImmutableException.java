package com.familya.treeaccess.domain.exception;

/**
 * Ngoại lệ ném khi có thao tác vi phạm luật bất biến owner (như hạ cấp hoặc
 * thu hồi quyền của chủ sở hữu cây).
 */
public class OwnerImmutableException extends RuntimeException {
    /**
     * @param message thông điệp mô tả vi phạm
     */
    public OwnerImmutableException(String message) { super(message); }
}