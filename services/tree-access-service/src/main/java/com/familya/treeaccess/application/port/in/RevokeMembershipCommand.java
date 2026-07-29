package com.familya.treeaccess.application.port.in;

import java.util.UUID;

public record RevokeMembershipCommand(UUID treeId, UUID userId, UUID revokedBy, String reason) { }