package com.familya.treeaccess.application.port.in;

import java.util.UUID;

public record InitiateDeleteTreeCommand(
        UUID treeId,
        UUID actingUser,
        long expectedTreeVersion,
        long expectedTreeEpoch,
        boolean placeRetentionHolds) {
}