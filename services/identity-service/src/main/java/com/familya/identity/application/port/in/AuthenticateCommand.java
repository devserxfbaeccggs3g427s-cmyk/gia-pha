package com.familya.identity.application.port.in;

public record AuthenticateCommand(
        String email,
        String password,
        String ipAddress,
        String userAgent
) { }
