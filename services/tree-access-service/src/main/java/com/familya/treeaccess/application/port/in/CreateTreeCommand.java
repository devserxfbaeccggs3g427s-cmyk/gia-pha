package com.familya.treeaccess.application.port.in;

import java.util.UUID;

/**
 * Lệnh tạo cây gia phả mới.
 *
 * @param ownerUserId UUID chủ sở hữu cây (luôn có quyền ADMIN)
 * @param name        tên cây, không rỗng
 */
public record CreateTreeCommand(UUID ownerUserId, String name) { }