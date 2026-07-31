package com.familya.treeaccess.application.port.in;

import java.util.UUID;

/**
 * Lệnh đóng băng cây, chặn mọi thao tác ghi.
 *
 * @param treeId          mã cây
 * @param actingUser      UUID người thực hiện (phải có ADMIN)
 * @param expectedVersion phiên bản tối thiểu kỳ vọng
 */
public record FreezeTreeCommand(UUID treeId, UUID actingUser, long expectedVersion) { }