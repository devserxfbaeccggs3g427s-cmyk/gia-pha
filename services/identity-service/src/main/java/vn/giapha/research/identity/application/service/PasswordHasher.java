package vn.giapha.research.identity.application.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt hashing with exact legacy compatibility (Task 17.2, Req 2.2/14).
 *
 * <p>Legacy hashes were produced by bcryptjs with cost 12 and the {@code $2a$}
 * version marker. New hashes therefore use {@code $2a$12} too, so a hash
 * written by Spring remains verifiable by the still-running legacy backend for
 * the whole coexistence window (one identity store, two verifiers).
 *
 * <p><strong>Rehash-on-login policy:</strong> after a successful password
 * verification, {@link #needsRehash} reports whether the stored hash deviates
 * from the target policy (unknown scheme or cost &ne; 12). The login flow then
 * upgrades the hash via a compare-and-set update — the only moment the
 * plaintext is available. Golden legacy hashes are already {@code $2a$12} and
 * are left untouched.
 *
 * <p>{@link #verify} treats a malformed stored hash as a failed attempt, never
 * a server error (legacy {@code auth-service.ts} catch-block parity).
 */
@Component
public class PasswordHasher {

    /** Frozen legacy timing-equalization hash ({@code auth-service.ts}). */
    public static final String DUMMY_HASH =
            "$2a$12$di2fvsHit2Jc5VufildE9e2gS2kh2KggSh7HYK8qOzRd.fb6hNb4.";

    private static final int COST = 12;
    private static final Pattern BCRYPT_FORMAT =
            Pattern.compile("\\A\\$(2[aby])\\$(\\d{2})\\$[./A-Za-z0-9]{53}");

    private final BCryptPasswordEncoder encoder =
            new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2A, COST);

    public String hash(CharSequence rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean verify(CharSequence rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, storedHash);
        } catch (RuntimeException malformedHash) {
            // Malformed legacy hash: failed attempt, never a 500.
            return false;
        }
    }

    /**
     * Burn one bcrypt verification against the dummy hash so the
     * "unknown email" and "wrong password" paths take comparable time.
     */
    public void spendDummyVerification(CharSequence rawPassword) {
        encoder.matches(rawPassword, DUMMY_HASH);
    }

    /** True when a successfully-verified hash should be upgraded to $2a/$2b cost 12. */
    public boolean needsRehash(String storedHash) {
        Matcher matcher = BCRYPT_FORMAT.matcher(storedHash);
        if (!matcher.matches()) {
            return true;
        }
        return Integer.parseInt(matcher.group(2)) != COST;
    }
}
