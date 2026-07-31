package com.familya.platform.projection;

import com.familya.platform.error.StaleProjectionException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Reusable authorization projection SDK. The SDK captures the four
 * checks every service must perform before authorising an action:
 *
 * <ol>
 *   <li>Is the projection row present? If not, fail closed for unsafe
 *       mutations; sensitive reads use a deadline-bound emergency
 *       RPC to the source service.</li>
 *   <li>Is the projection row's revision/epoch at or beyond the
 *       caller's expected version? If not, fail closed.</li>
 *   <li>Is the projection row's {@code lastUpdatedAt} within the
 *       freshness threshold? If not, fail closed for unsafe
 *       mutations.</li>
 *   <li>Is the row revoked? If yes, deny.</li>
 * </ol>
 *
 * <p>The SDK is intentionally framework-independent. Each service
 * provides its own {@link ProjectionReader}; the SDK composes the
 * policy. Services MUST NOT replace the SDK with ad-hoc checks because
 * the policy is the source of truth for the revocation objective.</p>
 */
public final class AuthorizationProjection {

    private final ProjectionReader reader;
    private final Duration freshnessThreshold;
    private final Duration emergencyRpcBudget;

    public AuthorizationProjection(ProjectionReader reader,
                                   Duration freshnessThreshold,
                                   Duration emergencyRpcBudget) {
        this.reader = reader;
        this.freshnessThreshold = freshnessThreshold;
        this.emergencyRpcBudget = emergencyRpcBudget;
    }

    public <T extends ProjectionRow> Decision<T> authorize(java.util.UUID aggregateId,
                                                            java.util.UUID userId,
                                                            long expectedRevision,
                                                            Class<T> rowType) {
        Optional<T> row = reader.find(aggregateId, userId, rowType);
        if (row.isEmpty()) {
            // No row — fail closed for unsafe mutations. Sensitive
            // reads may invoke the emergency RPC with
            // {@code emergencyRpcBudget} as the deadline.
            return Decision.absent();
        }
        T r = row.get();
        if (r.isRevoked()) {
            return Decision.deny(r, "revoked");
        }
        if (r.header().revision() < expectedRevision) {
            throw new StaleProjectionException(
                    "Projection revision " + r.header().revision() + " < expected " + expectedRevision);
        }
        if (Duration.between(r.header().lastUpdatedAt(), Instant.now()).compareTo(freshnessThreshold) > 0) {
            // Stale-by-freshness: unsafe mutations fail closed. The
            // caller may invoke the emergency RPC with the configured
            // budget.
            return Decision.stale(r, freshnessThreshold);
        }
        return Decision.allow(r);
    }

    public Duration emergencyRpcBudget() { return emergencyRpcBudget; }
    public Duration freshnessThreshold() { return freshnessThreshold; }
    public ProjectionReader reader() { return reader; }

    public interface ProjectionReader {
        <T extends ProjectionRow> Optional<T> find(java.util.UUID aggregateId,
                                                  java.util.UUID userId,
                                                  Class<T> rowType);
    }

    public interface ProjectionRow {
        ProjectionHeader header();
        boolean isRevoked();
    }

    public record Decision<T>(T row, State state, String reason) {
        public enum State { ALLOW, DENY, ABSENT, STALE }
        public boolean isAllowed() { return state == State.ALLOW; }
        public boolean isDenied() { return state == State.DENY; }
        public boolean isAbsent() { return state == State.ABSENT; }
        public boolean isStale() { return state == State.STALE; }
        public static <T> Decision<T> allow(T row) { return new Decision<>(row, State.ALLOW, "ok"); }
        public static <T> Decision<T> deny(T row, String why) { return new Decision<>(row, State.DENY, why); }
        public static <T> Decision<T> absent() { return new Decision<>(null, State.ABSENT, "absent"); }
        public static <T> Decision<T> stale(T row, Duration threshold) {
            return new Decision<>(row, State.STALE, "older than " + threshold.toSeconds() + "s");
        }
    }
}