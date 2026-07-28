package com.familya.event.application.port.in;

import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Migration loader input. Event records are loaded from immutable
 * Blob manifests with preserved IDs and timestamps.
 */
public record LoadEventCommand(
        UUID eventId,
        UUID treeId,
        String title,
        String description,
        DomainEvent.Kind kind,
        LocalDate startDate,
        LocalDate endDate,
        RecurrenceRule recurrence,
        UUID primaryMemberId,
        List<UUID> additionalMemberIds,
        List<UUID> mediaRefs,
        String location,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        boolean tombstoned,
        boolean replaySafe
) { }