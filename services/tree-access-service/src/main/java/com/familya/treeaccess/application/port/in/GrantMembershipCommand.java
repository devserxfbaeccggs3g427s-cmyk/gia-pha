package com.familya.treeaccess.application.port.in;

import com.familya.treeaccess.domain.model.TreeMembership;

import java.util.UUID;

/**
 * Lệnh cấp quyền thành viên trên cây.
 *
 * @param treeId    mã cây
 * @param userId    mã người dùng được cấp quyền
 * @param role      vai trò mới
 * @param grantedBy UUID người thực hiện cấp quyền (phải có ADMIN)
 */
public record GrantMembershipCommand(UUID treeId, UUID userId, TreeMembership.Role role, UUID grantedBy) { }