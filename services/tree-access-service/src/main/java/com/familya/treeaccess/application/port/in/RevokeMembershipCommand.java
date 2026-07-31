package com.familya.treeaccess.application.port.in;

import java.util.UUID;

/**
 * Lệnh thu hồi quyền thành viên.
 *
 * @param treeId    mã cây
 * @param userId    UUID thành viên bị thu hồi
 * @param revokedBy UUID người thực hiện (phải có ADMIN)
 * @param reason    lý do thu hồi (ghi log/audit)
 */
public record RevokeMembershipCommand(UUID treeId, UUID userId, UUID revokedBy, String reason) { }