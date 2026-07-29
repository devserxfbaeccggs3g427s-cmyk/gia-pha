package com.familya.identity.application.port.in;

import java.util.UUID;

public record VerifyEmailCommand(UUID userId, String token) { }
