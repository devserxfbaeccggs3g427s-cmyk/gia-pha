package com.familya.member.application.port.in;

import java.util.UUID;

public record TombstoneMemberCommand(UUID memberId, UUID actingUser, long expectedVersion, long expectedTreeRevision) { }