package com.familya.sharing.application.port.in;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PublicLookupQuery(
        String token,
        UUID mediaId,
        Instant now) {
}
