package com.familya.treeaccess.application.port.in;

import java.util.UUID;

public record UnfreezeTreeCommand(UUID treeId, UUID actingUser, long expectedVersion) { }