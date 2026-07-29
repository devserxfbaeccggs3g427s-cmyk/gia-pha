package com.familya.search.application.port.in;

import java.util.UUID;

public record SearchMediaQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String q,
        String kind,
        Boolean tombstoned,
        Integer limit) {
}
