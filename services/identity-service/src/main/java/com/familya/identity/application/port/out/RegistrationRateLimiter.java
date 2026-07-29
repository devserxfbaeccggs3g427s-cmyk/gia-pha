package com.familya.identity.application.port.out;

import java.time.Instant;

public interface RegistrationRateLimiter {
    boolean tryRegister(String ipAddress, Instant now);
}
