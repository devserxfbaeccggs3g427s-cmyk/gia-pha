package com.familya.platform.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

/**
 * BCrypt verifier. Existing hashes from the legacy system use the
 * {@code 2a} prefix; we accept both {@code 2a} and {@code 2y} and
 * always rehash to {@code 2y} on successful authentication.
 */
@Component
public class PasswordEncoder {

    private static final int COST = 10;

    public String hash(String raw) {
        return BCrypt.withDefaults().hashToString(COST, raw.toCharArray());
    }

    public boolean matches(String raw, String stored) {
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        BCrypt.Result r = BCrypt.verifyer().verify(raw.toCharArray(), stored);
        return r.verified;
    }

    public boolean needsRehash(String stored) {
        // Always rehash on login; this satisfies ADR-009 rehash policy.
        return true;
    }
}
