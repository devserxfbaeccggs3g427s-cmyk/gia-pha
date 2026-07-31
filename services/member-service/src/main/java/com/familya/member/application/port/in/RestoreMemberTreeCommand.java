package com.familya.member.application.port.in;

import java.util.UUID;

/**
 * Lệnh Saga bù trừ tương ứng với {@link PurgeMemberTreeCommand}. Khôi phục các thành viên
 * đã tombstone trong cây để rollback delete-tree Saga trước irreversible boundary.
 *
 * @param operationId mã operationId của Saga
 * @param treeId      mã cây cần khôi phục
 */
public record RestoreMemberTreeCommand(
        UUID operationId,
        UUID treeId) {
}