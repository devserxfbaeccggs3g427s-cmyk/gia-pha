package com.familya.treeaccess.application.port.in;

import java.util.UUID;

/**
 * Lệnh khởi tạo Saga xóa cây.
 *
 * @param treeId              mã cây cần xóa
 * @param actingUser          UUID người thực hiện (phải có ADMIN/owner)
 * @param expectedTreeVersion phiên bản cây kỳ vọng
 * @param expectedTreeEpoch   epoch cây kỳ vọng
 * @param placeRetentionHolds có đặt retention hold hay không
 */
public record InitiateDeleteTreeCommand(
        UUID treeId,
        UUID actingUser,
        long expectedTreeVersion,
        long expectedTreeEpoch,
        boolean placeRetentionHolds) {
}