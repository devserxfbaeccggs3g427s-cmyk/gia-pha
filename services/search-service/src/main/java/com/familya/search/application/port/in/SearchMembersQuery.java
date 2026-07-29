package com.familya.search.application.port.in;

import java.util.UUID;

public record SearchMembersQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String q,
        Integer birthYear,
        Integer birthYearFrom,
        Integer birthYearTo,
        Boolean tombstoned,
        Integer limit) {
}
