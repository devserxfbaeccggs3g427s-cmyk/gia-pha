package com.familya.identity.application.port.in;

import java.util.UUID;

public record RegisterUserCommand(
        String email,
        String password,
        String ipAddress,
        boolean verificationRequired
) {
    public RegisterUserCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("password must be at least 12 characters");
        }
    }

    public UUID userId() { return UUID.randomUUID(); }
}
