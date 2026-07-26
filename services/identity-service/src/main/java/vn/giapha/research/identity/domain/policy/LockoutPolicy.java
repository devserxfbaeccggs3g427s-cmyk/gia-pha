package vn.giapha.research.identity.domain.policy;

import java.time.Duration;
import java.time.Instant;

/**
 * Frozen legacy lockout policy (Task 17.1, {@code src/lib/auth/lockout.ts}):
 * 5 consecutive failures lock the account for 15 minutes.
 *
 * <p>Legacy quirks preserved exactly:
 * <ul>
 *   <li>A failure while the account is locked changes nothing.</li>
 *   <li>The first failure after a lock has <em>expired</em> restarts the
 *       counter at 1 (legacy resets {@code previousAttempts} to 0 whenever
 *       {@code lockedUntil} is set, expired or not).</li>
 *   <li>A successful login clears both the counter and the lock.</li>
 * </ul>
 *
 * <p>Pure functions over immutable state; the row-lock serialization that
 * makes concurrent failures accurate lives in the repository (Task 19.3).
 */
public final class LockoutPolicy {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private LockoutPolicy() {}

    public static boolean isLocked(Instant lockedUntil, Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counter/lock state to persist after one more failed attempt. */
    public record FailureOutcome(int failedLoginAttempts, Instant lockedUntil, boolean locked) {}

    public static FailureOutcome recordFailure(int failedLoginAttempts, Instant lockedUntil,
            Instant now) {
        if (isLocked(lockedUntil, now)) {
            return new FailureOutcome(failedLoginAttempts, lockedUntil, true);
        }
        int previousAttempts = lockedUntil != null ? 0 : failedLoginAttempts;
        int attempts = previousAttempts + 1;
        boolean shouldLock = attempts >= MAX_FAILED_ATTEMPTS;
        return new FailureOutcome(attempts, shouldLock ? now.plus(LOCK_DURATION) : null,
                shouldLock);
    }
}
