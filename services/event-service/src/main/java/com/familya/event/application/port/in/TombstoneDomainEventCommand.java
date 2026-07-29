package com.familya.event.application.port.in;

import java.util.UUID;

public record TombstoneDomainEventCommand(UUID eventId, UUID actingUser, long expectedVersion, long expectedTreeRevision) { }