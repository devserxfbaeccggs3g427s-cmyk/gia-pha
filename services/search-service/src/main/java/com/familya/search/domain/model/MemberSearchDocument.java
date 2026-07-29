package com.familya.search.domain.model;

import java.time.Instant;
import java.util.UUID;

public record MemberSearchDocument(
        UUID treeId,
        UUID memberId,
        String fullName,
        String givenName,
        String surname,
        Integer birthYear,
        Integer deathYear,
        boolean tombstoned,
        Instant lastUpdated
) { }
