package com.familya.treeaccess.domain.exception;

/**
 * Ngoại lệ ném khi cố gắng thực hiện thao tác ghi trên cây đang ở trạng thái
 * FROZEN hoặc TOMBSTONED.
 */
public class TreeFrozenException extends RuntimeException {
    /**
     * @param message thông điệp mô tả trạng thái cây
     */
    public TreeFrozenException(String message) { super(message); }
}