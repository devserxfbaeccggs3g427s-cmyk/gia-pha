package com.familya.search.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EventSearchDocument(
        UUID treeId,
        UUID eventId,
        String title,
        LocalDate startDate,
        String kind,
        boolean tombstoned,
        Instant lastUpdated
) { }
