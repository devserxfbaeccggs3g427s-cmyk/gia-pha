package vn.giapha.research.identity.application.bridge;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridge {@code jti} replay protection (Task 18.3, Req 2.7).
 *
 * <p>State machine:
 * <ul>
 *   <li>{@link #register(String, Instant)} marks a token as pending verification;</li>
 *   <li>{@link #consume(String, Instant)} marks it as seen — only the first
 *       call wins; every subsequent call (replay) is rejected.</li>
 * </ul>
 *
 * <p>Memory is bounded by the bridge max-lifetime: a periodic sweep evicts
 * entries whose {@code exp} is in the past. In-memory backing is sufficient
 * because a Spring pod restart simply resets the cache; outstanding tokens
 * (max 5 minutes) re-authenticate naturally on the next request.
 */
public class BridgeReplayStore {

    private final Map<String, Instant> pending = new ConcurrentHashMap<>();
    private final Duration sweepAfter;
    private final AtomicBoolean sweeping = new AtomicBoolean(false);

    public BridgeReplayStore() {
        this(Duration.ofMinutes(10));
    }

    public BridgeReplayStore(Duration sweepAfter) {
        if (sweepAfter == null || sweepAfter.isNegative() || sweepAfter.isZero()) {
            throw new IllegalArgumentException("sweepAfter must be positive");
        }
        this.sweepAfter = sweepAfter;
    }

    public void register(String tokenId, Instant expiresAt) {
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("tokenId is required");
        }
        pending.put(tokenId, expiresAt);
    }

    /**
     * @return {@code true} when this call won the single-use race; {@code false}
     *         when the token id was already consumed or expired.
     */
    public boolean consume(String tokenId, Instant expiresAt) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        Instant expiry = pending.get(tokenId);
        if (expiry == null) {
            return false;
        }
        if (!expiry.equals(expiresAt)) {
            return false;
        }
        boolean removed = pending.remove(tokenId) != null;
        sweep();
        return removed;
    }

    /** Count currently registered token ids — exposed for tests/observability. */
    public int size() {
        return pending.size();
    }

    private void sweep() {
        if (!sweeping.compareAndSet(false, true)) {
            return;
        }
        try {
            Instant cutoff = Instant.now().minus(sweepAfter);
            Iterator<Map.Entry<String, Instant>> it = pending.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Instant> entry = it.next();
                if (entry.getValue().isBefore(cutoff)) {
                    it.remove();
                }
            }
        } finally {
            sweeping.set(false);
        }
    }
}
