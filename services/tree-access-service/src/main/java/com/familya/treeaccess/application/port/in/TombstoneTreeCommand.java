package com.familya.treeaccess.application.port.in;

import java.util.UUID;

public record TombstoneTreeCommand(UUID treeId, UUID actingUser, long expectedVersion) { }