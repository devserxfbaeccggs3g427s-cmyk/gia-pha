package com.familya.treeaccess.application.port.in;

import java.util.UUID;

/**
 * Lệnh bỏ đóng băng cây, đưa về trạng thái ACTIVE.
 *
 * @param treeId          mã cây
 * @param actingUser      UUID người thực hiện (phải có ADMIN)
 * @param expectedVersion phiên bản kỳ vọng
 */
public record UnfreezeTreeCommand(UUID treeId, UUID actingUser, long expectedVersion) { }