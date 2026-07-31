package com.familya.treeaccess.application.port.in;

import java.util.UUID;

/**
 * Lệnh đánh dấu tombstone cho cây — bước không thể thu hồi.
 *
 * @param treeId          mã cây
 * @param actingUser      UUID người thực hiện (phải có ADMIN)
 * @param expectedVersion phiên bản kỳ vọng
 */
public record TombstoneTreeCommand(UUID treeId, UUID actingUser, long expectedVersion) { }