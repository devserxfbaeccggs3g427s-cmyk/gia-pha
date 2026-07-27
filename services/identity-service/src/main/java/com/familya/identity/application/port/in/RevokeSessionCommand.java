package com.familya.identity.application.port.in;

import java.util.UUID;

public record RevokeSessionCommand(UUID sessionId, UUID actingUserId) { }
