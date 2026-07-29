package com.familya.treeaccess.application.port.in;

import java.util.UUID;

public record AdvanceRevisionCommand(UUID treeId, UUID actingUser, long expectedVersion, long newRevision, long newEpoch, String reason) { }