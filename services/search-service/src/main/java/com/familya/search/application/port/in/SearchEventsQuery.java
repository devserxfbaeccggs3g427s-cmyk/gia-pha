package com.familya.search.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

public record SearchEventsQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String q,
        String kind,
        LocalDate from,
        LocalDate to,
        Boolean tombstoned,
        Integer limit) {
}
