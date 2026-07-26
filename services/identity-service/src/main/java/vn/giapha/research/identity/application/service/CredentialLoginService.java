package vn.giapha.research.identity.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.identity.infrastructure.config.ResearchProperties;
import vn.giapha.research.identity.application.port.out.UserRepository;
import vn.giapha.research.identity.domain.auth.AuthSession;
import vn.giapha.research.identity.domain.model.NewUser;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.domain.policy.LockoutPolicy;
import vn.giapha.research.identity.domain.policy.LockoutPolicy.FailureOutcome;
import vn.giapha.research.identity.infrastructure.kernel.error.ConflictException;
import vn.giapha.research.identity.infrastructure.kernel.error.UnauthorizedException;

/**
 * Credential login (Task 19.3, Req 2.3). Parity with the legacy
 * {@code auth-service.ts}:
 *
 * <ul>
 *   <li>{@link PasswordHasher#spendDummyVerification} runs even when the user
 *       is unknown so timing leaks don't reveal account existence;</li>
 *   <li>the user row is locked via {@code findByEmailForUpdate} so
 *       concurrent failures serialize through the lockout counter;</li>
 *   <li>{@link LockoutPolicy#recordFailure} decides the next counter /
 *       {@code lockedUntil} value, preserving the first-failure-after-expiry
 *       quirk;</li>
 *   <li>after a successful {@link PasswordHasher#verify}, the hash is
 *       upgraded on the spot via {@code needsRehash} (Task 17.2);</li>
 *   <li>the {@code EMAIL_NOT_VERIFIED} contract fires only when the policy
 *       flag is enabled.</li>
 * </ul>
 */
@Service
public class CredentialLoginService {

    private static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    private static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    private static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AuthSessionService sessions;
    private final boolean requireEmailVerification;

    public CredentialLoginService(UserRepository users, PasswordHasher hasher,
            AuthSessionService sessions, ResearchProperties properties) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.requireEmailVerification = properties.auth().requireEmailVerification();
    }

    @Transactional
    public LoginOutcome login(String email, String password, Instant now) {
        String normalized = NewUser.normalizeEmail(email);
        Optional<User> maybeUser = users.findByEmailForUpdate(normalized);
        if (maybeUser.isEmpty() || !maybeUser.get().hasPassword()) {
            // Even on unknown accounts / OAuth-only accounts we burn the same
            // bcrypt work to keep timing leaks stable (legacy parity).
            hasher.spendDummyVerification(password == null ? "" : password);
            throw new UnauthorizedException(INVALID_CREDENTIALS, "Invalid credentials");
        }
        User user = maybeUser.get();
        if (LockoutPolicy.isLocked(user.lockedUntil(), now)) {
            throw new ConflictException(ACCOUNT_LOCKED, "Account is locked",
                    lockDetails(user, user.lockedUntil()));
        }
        boolean matches;
        try {
            matches = hasher.verify(password, user.passwordHash());
        } catch (RuntimeException malformed) {
            matches = false;
        }
        if (!matches) {
            FailureOutcome outcome = LockoutPolicy.recordFailure(
                    user.failedLoginAttempts(), user.lockedUntil(), now);
            users.updateLockoutState(user.userKey(),
                    outcome.failedLoginAttempts(), outcome.lockedUntil(), now);
            if (outcome.locked()) {
                throw new ConflictException(ACCOUNT_LOCKED, "Account is locked",
                        lockDetails(user, outcome.lockedUntil()));
            }
            throw new UnauthorizedException(INVALID_CREDENTIALS, "Invalid credentials");
        }
        if (requireEmailVerification && !user.emailVerified()) {
            throw new ConflictException(EMAIL_NOT_VERIFIED, "Email is not verified");
        }
        // Rehash-on-login: legacy $2a$12 hashes pass straight through; older
        // schemes get upgraded in place.
        if (user.hasPassword() && hasher.needsRehash(user.passwordHash())) {
            String newHash = hasher.hash(password);
            users.updatePasswordHash(user.userKey(), user.passwordHash(), newHash, now);
        }
        // Email verification: stamp the row when the policy is disabled and
        // the user has no prior verification (legacy auth-service parity).
        Instant verifiedAt = user.emailVerifiedAt() != null
                ? user.emailVerifiedAt()
                : (requireEmailVerification ? null : now);
        users.recordLoginSuccess(user.userKey(), verifiedAt, now);
        AuthSessionService.IssuedSession issued = sessions.issue(user, Set.of(), now);
        return new LoginOutcome(user, issued);
    }

    public record LoginOutcome(User user, AuthSessionService.IssuedSession session) {}

    /** Sub-record attached to {@code ACCOUNT_LOCKED} responses. */
    public record LockContext(User user, Instant lockedUntil) {}

    private static java.util.Map<String, Object> lockDetails(User user, Instant lockedUntil) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("lockedUntil", lockedUntil == null ? null : lockedUntil.toString());
        details.put("userExternalId", user.externalId());
        return details;
    }

    /** Used by tests to verify the dummy-burn timing. */
    static Duration dummyBurnBudget() {
        return Duration.ofMillis(50);
    }
}
