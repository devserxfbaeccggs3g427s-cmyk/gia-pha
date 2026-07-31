package com.familya.member.application.port.in;

import java.util.UUID;

/**
 * Saga command from Tree Access's delete-tree Saga. Tombstones every
 * non-tombstoned member in the tree and persists a compensation snapshot
 * for restoration before the irreversible boundary.
 */
/**
 * Lệnh Saga từ delete-tree Saga của Tree Access. Tombstone mọi thành viên chưa tombstone
 * trong cây và lưu snapshot bù trừ để phục vụ khôi phục trước khi qua ngưỡng không thể
 * đảo ngược (irreversible boundary).
 *
 * @param operationId           mã operationId của Saga
 * @param treeId                mã cây cần purge
 * @param targetAggregateVersion phiên bản aggregate mục tiêu
 * @param targetEpoch           epoch mục tiêu
 */
public record PurgeMemberTreeCommand(
        UUID operationId,
        UUID treeId,
        long targetAggregateVersion,
        long targetEpoch) {
}