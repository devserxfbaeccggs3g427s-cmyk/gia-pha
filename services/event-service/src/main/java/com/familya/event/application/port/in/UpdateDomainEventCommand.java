package com.familya.event.application.port.in;

import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpdateDomainEventCommand(
        UUID eventId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        String title,
        String description,
        DomainEvent.Kind kind,
        LocalDate startDate,
        LocalDate endDate,
        RecurrenceRule recurrence,
        UUID primaryMemberId,
        List<UUID> additionalMemberIds,
        List<UUID> mediaRefs,
        String location
) { }