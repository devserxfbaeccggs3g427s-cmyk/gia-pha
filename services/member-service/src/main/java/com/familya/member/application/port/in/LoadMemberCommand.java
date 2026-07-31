package com.familya.member.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Migration loader input. Members are loaded from immutable Blob
 * manifests with preserved IDs and timestamps. The loader is idempotent
 * per memberId; re-running does not duplicate effects.
 */
public record LoadMemberCommand(
        UUID memberId,
        UUID treeId,
        UUID userId,
        String displayName,
        String givenName,
        String surname,
        LocalDate birthDate,
        LocalDate deathDate,
        boolean birthYearKnown,
        boolean deathYearKnown,
        String gender,
        String status,
        Integer generation,
        String legacyAvatarUrl,
        String notes,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        boolean tombstoned,
        boolean replaySafe
) { }