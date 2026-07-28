package com.familya.member.application.port.in;

import java.util.UUID;

public record MergeMembersCommand(UUID survivorId, UUID sourceMemberId, UUID actingUser, long expectedVersion, long expectedTreeRevision) { }