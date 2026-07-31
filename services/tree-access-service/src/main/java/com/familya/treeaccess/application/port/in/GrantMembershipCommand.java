package com.familya.treeaccess.application.port.in;

import com.familya.treeaccess.domain.model.TreeMembership;

import java.util.UUID;

public record GrantMembershipCommand(UUID treeId, UUID userId, TreeMembership.Role role, UUID grantedBy) { }